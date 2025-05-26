package com.tejalabs.falldetection.services;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import com.tejalabs.falldetection.utils.DataLogger;
import com.tejalabs.falldetection.utils.EmergencyManager;
import com.tejalabs.falldetection.utils.NotificationHelper;
import com.tejalabs.falldetection.utils.SensorDataCollector;
import com.tejalabs.falldetection.utils.SharedPreferencesManager;
import com.tejalabs.falldetection.utils.TinyMLProcessor;

/**
 * Foreground service for continuous fall detection monitoring
 * Handles sensor data collection, ML processing, and emergency response
 */
public class FallDetectionService extends Service implements
    SensorDataCollector.SensorDataListener,
    EmergencyManager.EmergencyListener {

    private static final String TAG = "FallDetectionService";

    // Service actions
    public static final String ACTION_START_MONITORING = "com.tejalabs.falldetection.START_MONITORING";
    public static final String ACTION_STOP_MONITORING = "com.tejalabs.falldetection.STOP_MONITORING";
    public static final String ACTION_CANCEL_EMERGENCY = "com.tejalabs.falldetection.CANCEL_EMERGENCY";

    // Service state
    private boolean isMonitoring = false;
    private boolean isServiceRunning = false;

    // Core components
    private SensorDataCollector sensorCollector;
    private TinyMLProcessor mlProcessor;
    private EmergencyManager emergencyManager;
    private NotificationHelper notificationHelper;
    private SharedPreferencesManager prefsManager;
    private DataLogger dataLogger;

    // System components
    private PowerManager.WakeLock wakeLock;
    private BroadcastReceiver emergencyCancelReceiver;

    // Service binder for UI communication
    private final IBinder binder = new FallDetectionBinder();

    // Service listener interface
    public interface ServiceListener {
        void onServiceStateChanged(boolean isRunning, boolean isMonitoring);
        void onFallDetected(float confidence);
        void onEmergencyStateChanged(boolean isActive, int countdown);
    }

    private ServiceListener serviceListener;

    public class FallDetectionBinder extends Binder {
        public FallDetectionService getService() {
            return FallDetectionService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Log.d(TAG, "Fall Detection Service created");

        // Initialize components
        initializeComponents();

        // Register broadcast receivers
        registerReceivers();

        // Acquire wake lock
        acquireWakeLock();

        isServiceRunning = true;

        // Log service start
        dataLogger.logServiceEvent("FallDetectionService", "created", "Service initialized");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service start command received");

        if (intent != null) {
            String action = intent.getAction();

            if (ACTION_START_MONITORING.equals(action)) {
                startMonitoring();
            } else if (ACTION_STOP_MONITORING.equals(action)) {
                stopMonitoring();
            } else if (ACTION_CANCEL_EMERGENCY.equals(action)) {
                cancelEmergency();
            }
        }

        // Start foreground service
        startForeground(NotificationHelper.NOTIFICATION_ID_FOREGROUND_SERVICE,
            notificationHelper.createForegroundServiceNotification(isMonitoring));

        // Update service state
        prefsManager.setServiceRunning(true);

        // Return sticky to restart service if killed
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Service bound");
        return binder;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Fall Detection Service destroyed");

        // Stop monitoring
        stopMonitoring();

        // Clean up components
        cleanupComponents();

        // Unregister receivers
        unregisterReceivers();

        // Release wake lock
        releaseWakeLock();

        // Update service state
        prefsManager.setServiceRunning(false);
        isServiceRunning = false;

        // Log service stop
        dataLogger.logServiceEvent("FallDetectionService", "destroyed", "Service stopped");

        super.onDestroy();
    }

    /**
     * Initialize all service components
     */
    private void initializeComponents() {
        try {
            prefsManager = SharedPreferencesManager.getInstance(this);
            notificationHelper = NotificationHelper.getInstance(this);
            dataLogger = DataLogger.getInstance(this);

            sensorCollector = new SensorDataCollector(this);
            sensorCollector.setDataListener(this);

            mlProcessor = new TinyMLProcessor(this);

            emergencyManager = new EmergencyManager(this);
            emergencyManager.setEmergencyListener(this);

            Log.d(TAG, "Service components initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing service components", e);
            // Initialize minimal components to prevent crashes
            try {
                prefsManager = SharedPreferencesManager.getInstance(this);
                notificationHelper = NotificationHelper.getInstance(this);
                dataLogger = DataLogger.getInstance(this);
            } catch (Exception fallbackError) {
                Log.e(TAG, "Critical error in service initialization", fallbackError);
            }
        }
    }

    /**
     * Register broadcast receivers
     */
    private void registerReceivers() {
        // Emergency cancel receiver
        emergencyCancelReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (ACTION_CANCEL_EMERGENCY.equals(intent.getAction())) {
                    cancelEmergency();
                }
            }
        };

        IntentFilter filter = new IntentFilter(ACTION_CANCEL_EMERGENCY);
        registerReceiver(emergencyCancelReceiver, filter);
    }

    /**
     * Unregister broadcast receivers
     */
    private void unregisterReceivers() {
        if (emergencyCancelReceiver != null) {
            try {
                unregisterReceiver(emergencyCancelReceiver);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Receiver not registered", e);
            }
        }
    }

    /**
     * Acquire wake lock to keep service running
     */
    private void acquireWakeLock() {
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "FallDetection::ServiceWakeLock"
            );
            wakeLock.acquire(10*60*1000L /*10 minutes*/);
            Log.d(TAG, "Wake lock acquired");
        }
    }

    /**
     * Release wake lock
     */
    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            Log.d(TAG, "Wake lock released");
        }
    }

    /**
     * Start fall detection monitoring
     */
    public void startMonitoring() {
        if (isMonitoring) {
            Log.w(TAG, "Monitoring already started");
            return;
        }

        if (!prefsManager.isFallDetectionEnabled()) {
            Log.w(TAG, "Fall detection is disabled in settings");
            return;
        }

        Log.i(TAG, "Starting fall detection monitoring");

        // Start sensor data collection
        boolean sensorStarted = sensorCollector.startCollection();
        if (!sensorStarted) {
            Log.e(TAG, "Failed to start sensor collection");
            dataLogger.logError("FallDetectionService", "Failed to start sensors", null);
            return;
        }

        isMonitoring = true;

        // Update notification
        notificationHelper.updateForegroundServiceNotification("Monitoring for falls...");

        // Notify listener
        if (serviceListener != null) {
            serviceListener.onServiceStateChanged(isServiceRunning, isMonitoring);
        }

        // Log monitoring start
        dataLogger.logServiceEvent("FallDetectionService", "monitoring_started",
            "Sensitivity: " + prefsManager.getSensitivityLevel());

        Log.i(TAG, "Fall detection monitoring started");
    }

    /**
     * Stop fall detection monitoring
     */
    public void stopMonitoring() {
        if (!isMonitoring) {
            Log.w(TAG, "Monitoring not active");
            return;
        }

        Log.i(TAG, "Stopping fall detection monitoring");

        // Stop sensor data collection
        sensorCollector.stopCollection();

        // Cancel any active emergency
        emergencyManager.cancelEmergencyResponse();

        isMonitoring = false;

        // Update notification
        notificationHelper.updateForegroundServiceNotification("Monitoring paused");

        // Notify listener
        if (serviceListener != null) {
            serviceListener.onServiceStateChanged(isServiceRunning, isMonitoring);
        }

        // Log monitoring stop
        dataLogger.logServiceEvent("FallDetectionService", "monitoring_stopped", "User requested");

        Log.i(TAG, "Fall detection monitoring stopped");
    }

    /**
     * Cancel emergency response
     */
    public void cancelEmergency() {
        Log.i(TAG, "Cancelling emergency response");
        emergencyManager.cancelEmergencyResponse();
    }

    /**
     * Clean up all components
     */
    private void cleanupComponents() {
        if (sensorCollector != null) {
            sensorCollector.stopCollection();
        }

        if (mlProcessor != null) {
            mlProcessor.cleanup();
        }

        if (emergencyManager != null) {
            emergencyManager.cleanup();
        }

        if (dataLogger != null) {
            dataLogger.cleanup();
        }
    }

    // SensorDataListener implementation
    @Override
    public void onDataProcessed(SensorDataCollector.SensorDataWindow dataWindow) {
        if (!isMonitoring) {
            return;
        }

        // Process data with ML model
        TinyMLProcessor.FallDetectionResult result = mlProcessor.processSensorData(dataWindow);

        // Log sensor data occasionally
        dataLogger.logSensorData(dataWindow);

        // Check for fall detection
        if (result.isFall) {
            onFallDetected(result.confidence);
        }
    }

    @Override
    public void onFallDetected(float confidence) {
        Log.w(TAG, "Fall detected with confidence: " + confidence);

        // Log fall detection event
        dataLogger.logFallDetectionEvent(confidence, mlProcessor.getModelInfo(), false, false);

        // Start emergency response
        emergencyManager.startEmergencyResponse(confidence);

        // Notify listener
        if (serviceListener != null) {
            serviceListener.onFallDetected(confidence);
        }
    }

    // EmergencyListener implementation
    @Override
    public void onCountdownStarted(int seconds) {
        Log.i(TAG, "Emergency countdown started: " + seconds + " seconds");

        if (serviceListener != null) {
            serviceListener.onEmergencyStateChanged(true, seconds);
        }
    }

    @Override
    public void onCountdownTick(int remainingSeconds) {
        if (serviceListener != null) {
            serviceListener.onEmergencyStateChanged(true, remainingSeconds);
        }
    }

    @Override
    public void onCountdownCancelled() {
        Log.i(TAG, "Emergency countdown cancelled");

        if (serviceListener != null) {
            serviceListener.onEmergencyStateChanged(false, 0);
        }
    }

    @Override
    public void onEmergencyActivated() {
        Log.w(TAG, "Emergency procedures activated");

        if (serviceListener != null) {
            serviceListener.onEmergencyStateChanged(true, 0);
        }
    }

    @Override
    public void onEmergencyCompleted() {
        Log.i(TAG, "Emergency procedures completed");

        if (serviceListener != null) {
            serviceListener.onEmergencyStateChanged(false, 0);
        }
    }

    // Public methods for UI interaction
    public void setServiceListener(ServiceListener listener) {
        this.serviceListener = listener;
    }

    public boolean isMonitoring() {
        return isMonitoring;
    }

    public boolean isServiceRunning() {
        return isServiceRunning;
    }

    public String getServiceStatus() {
        try {
            if (!isServiceRunning) {
                return "Service not running";
            } else if (isMonitoring) {
                return "Monitoring active";
            } else {
                return "Service running - monitoring paused";
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting service status", e);
            return "Service status unknown";
        }
    }

    public EmergencyManager getEmergencyManager() {
        try {
            return emergencyManager;
        } catch (Exception e) {
            Log.e(TAG, "Error getting emergency manager", e);
            return null;
        }
    }
}
