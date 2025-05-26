package com.tejalabs.falldetection.utils;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handles logging of fall detection events, sensor data, and system events
 * Provides data export and analysis capabilities
 */
public class DataLogger {

    private static final String TAG = "DataLogger";
    private static DataLogger instance;

    // File names and directories
    private static final String LOG_DIR = "fall_detection_logs";
    private static final String EVENTS_LOG = "events.log";
    private static final String SENSOR_LOG = "sensor_data.log";
    private static final String EMERGENCY_LOG = "emergency_events.log";
    private static final String SYSTEM_LOG = "system_events.log";

    private Context context;
    private File logDirectory;
    private Gson gson;
    private ExecutorService executorService;
    private SimpleDateFormat dateFormat;

    // Log entry classes
    public static class LogEntry {
        public long timestamp;
        public String type;
        public String message;
        public Object data;

        public LogEntry(String type, String message, Object data) {
            this.timestamp = System.currentTimeMillis();
            this.type = type;
            this.message = message;
            this.data = data;
        }
    }

    public static class FallDetectionEvent {
        public long timestamp;
        public float confidence;
        public String detectionMethod;
        public boolean wasEmergency;
        public boolean wasCancelled;
        public long responseTime;

        public FallDetectionEvent(float confidence, String detectionMethod) {
            this.timestamp = System.currentTimeMillis();
            this.confidence = confidence;
            this.detectionMethod = detectionMethod;
            this.wasEmergency = false;
            this.wasCancelled = false;
            this.responseTime = 0;
        }
    }

    public static class EmergencyEvent {
        public long startTime;
        public long endTime;
        public String outcome;
        public int contactsNotified;
        public boolean callMade;
        public String location;

        public EmergencyEvent(long startTime) {
            this.startTime = startTime;
            this.endTime = 0;
            this.outcome = "pending";
            this.contactsNotified = 0;
            this.callMade = false;
            this.location = "unknown";
        }
    }

    public static class SensorDataEntry {
        public long timestamp;
        public float[] accelerometer;
        public float[] gyroscope;
        public float[] magnetometer;
        public float[] features;

        public SensorDataEntry(long timestamp, float[] accelerometer, float[] gyroscope, float[] magnetometer, float[] features) {
            this.timestamp = timestamp;
            this.accelerometer = accelerometer;
            this.gyroscope = gyroscope;
            this.magnetometer = magnetometer;
            this.features = features;
        }
    }

    private DataLogger(Context context) {
        this.context = context.getApplicationContext();
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .setDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
            .create();
        this.executorService = Executors.newSingleThreadExecutor();
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());

        initializeLogDirectory();
    }

    public static synchronized DataLogger getInstance(Context context) {
        if (instance == null) {
            instance = new DataLogger(context);
        }
        return instance;
    }

    /**
     * Initialize log directory
     */
    private void initializeLogDirectory() {
        try {
            File appDir = new File(context.getFilesDir(), LOG_DIR);
            if (!appDir.exists()) {
                boolean created = appDir.mkdirs();
                if (!created) {
                    Log.e(TAG, "Failed to create log directory");
                    return;
                }
            }

            logDirectory = appDir;
            Log.d(TAG, "Log directory initialized: " + logDirectory.getAbsolutePath());

        } catch (Exception e) {
            Log.e(TAG, "Error initializing log directory", e);
        }
    }

    /**
     * Log a fall detection event
     */
    public void logFallDetectionEvent(float confidence, String detectionMethod, boolean wasEmergency, boolean wasCancelled) {
        FallDetectionEvent event = new FallDetectionEvent(confidence, detectionMethod);
        event.wasEmergency = wasEmergency;
        event.wasCancelled = wasCancelled;

        LogEntry entry = new LogEntry("FALL_DETECTION", "Fall detected", event);
        writeLogEntry(EVENTS_LOG, entry);

        Log.i(TAG, String.format("Fall detection logged - Confidence: %.3f, Method: %s, Emergency: %s, Cancelled: %s",
            confidence, detectionMethod, wasEmergency, wasCancelled));
    }

    /**
     * Log an emergency event
     */
    public void logEmergencyEvent(long startTime, long endTime, String outcome) {
        EmergencyEvent event = new EmergencyEvent(startTime);
        event.endTime = endTime;
        event.outcome = outcome;

        LogEntry entry = new LogEntry("EMERGENCY", "Emergency response", event);
        writeLogEntry(EMERGENCY_LOG, entry);

        Log.i(TAG, "Emergency event logged - Duration: " + (endTime - startTime) + "ms, Outcome: " + outcome);
    }

    /**
     * Log sensor data (for debugging and analysis)
     */
    public void logSensorData(SensorDataCollector.SensorDataWindow dataWindow) {
        if (dataWindow == null || dataWindow.accelerometerData.isEmpty()) {
            return;
        }

        // Log only a sample of sensor data to avoid excessive file sizes
        if (Math.random() < 0.1) { // Log 10% of sensor data
            // Convert Float[] to float[] for the first sample
            Float[] accelSample = dataWindow.accelerometerData.get(0);
            float[] accelArray = new float[]{accelSample[0], accelSample[1], accelSample[2]};

            float[] gyroArray = null;
            if (!dataWindow.gyroscopeData.isEmpty()) {
                Float[] gyroSample = dataWindow.gyroscopeData.get(0);
                gyroArray = new float[]{gyroSample[0], gyroSample[1], gyroSample[2]};
            }

            float[] magArray = null;
            if (!dataWindow.magnetometerData.isEmpty()) {
                Float[] magSample = dataWindow.magnetometerData.get(0);
                magArray = new float[]{magSample[0], magSample[1], magSample[2]};
            }

            SensorDataEntry entry = new SensorDataEntry(
                System.currentTimeMillis(),
                accelArray,
                gyroArray,
                magArray,
                dataWindow.features
            );

            LogEntry logEntry = new LogEntry("SENSOR_DATA", "Sensor data sample", entry);
            writeLogEntry(SENSOR_LOG, logEntry);
        }
    }

    /**
     * Log system events
     */
    public void logSystemEvent(String eventType, String message, Object data) {
        LogEntry entry = new LogEntry(eventType, message, data);
        writeLogEntry(SYSTEM_LOG, entry);

        Log.d(TAG, "System event logged - Type: " + eventType + ", Message: " + message);
    }

    /**
     * Log service start/stop events
     */
    public void logServiceEvent(String serviceName, String action, String details) {
        String message = String.format("Service %s: %s", serviceName, action);
        logSystemEvent("SERVICE", message, details);
    }

    /**
     * Log permission events
     */
    public void logPermissionEvent(String permission, boolean granted) {
        String message = String.format("Permission %s: %s", permission, granted ? "granted" : "denied");
        logSystemEvent("PERMISSION", message, null);
    }

    /**
     * Log error events
     */
    public void logError(String component, String error, Exception exception) {
        String message = String.format("Error in %s: %s", component, error);
        String stackTrace = exception != null ? Log.getStackTraceString(exception) : null;
        logSystemEvent("ERROR", message, stackTrace);
    }

    /**
     * Write log entry to file
     */
    private void writeLogEntry(String filename, LogEntry entry) {
        if (logDirectory == null) {
            return;
        }

        executorService.execute(() -> {
            try {
                File logFile = new File(logDirectory, filename);

                // Create file if it doesn't exist
                if (!logFile.exists()) {
                    boolean created = logFile.createNewFile();
                    if (!created) {
                        Log.e(TAG, "Failed to create log file: " + filename);
                        return;
                    }
                }

                // Check file size and rotate if necessary
                if (logFile.length() > 10 * 1024 * 1024) { // 10MB limit
                    rotateLogFile(logFile);
                }

                // Write log entry
                try (FileWriter writer = new FileWriter(logFile, true)) {
                    String timestamp = dateFormat.format(new Date(entry.timestamp));
                    String logLine = String.format("[%s] %s: %s\n", timestamp, entry.type, entry.message);

                    writer.write(logLine);

                    // Write data if present
                    if (entry.data != null) {
                        String dataJson = gson.toJson(entry.data);
                        writer.write("Data: " + dataJson + "\n");
                    }

                    writer.write("---\n");
                    writer.flush();
                }

            } catch (IOException e) {
                Log.e(TAG, "Error writing to log file: " + filename, e);
            }
        });
    }

    /**
     * Rotate log file when it gets too large
     */
    private void rotateLogFile(File logFile) {
        try {
            String filename = logFile.getName();
            String baseName = filename.substring(0, filename.lastIndexOf('.'));
            String extension = filename.substring(filename.lastIndexOf('.'));

            // Create backup filename with timestamp
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String backupName = baseName + "_" + timestamp + extension;

            File backupFile = new File(logFile.getParent(), backupName);

            // Rename current file to backup
            boolean renamed = logFile.renameTo(backupFile);
            if (renamed) {
                Log.d(TAG, "Log file rotated: " + backupName);
            } else {
                Log.w(TAG, "Failed to rotate log file: " + filename);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error rotating log file", e);
        }
    }

    /**
     * Get log directory path
     */
    public String getLogDirectoryPath() {
        return logDirectory != null ? logDirectory.getAbsolutePath() : "Not available";
    }

    /**
     * Get log file size in bytes
     */
    public long getLogFileSize(String filename) {
        if (logDirectory == null) {
            return 0;
        }

        File logFile = new File(logDirectory, filename);
        return logFile.exists() ? logFile.length() : 0;
    }

    /**
     * Get total log directory size
     */
    public long getTotalLogSize() {
        if (logDirectory == null || !logDirectory.exists()) {
            return 0;
        }

        long totalSize = 0;
        File[] files = logDirectory.listFiles();
        if (files != null) {
            for (File file : files) {
                totalSize += file.length();
            }
        }

        return totalSize;
    }

    /**
     * Clear all log files
     */
    public void clearAllLogs() {
        if (logDirectory == null || !logDirectory.exists()) {
            return;
        }

        executorService.execute(() -> {
            File[] files = logDirectory.listFiles();
            if (files != null) {
                for (File file : files) {
                    boolean deleted = file.delete();
                    if (deleted) {
                        Log.d(TAG, "Deleted log file: " + file.getName());
                    }
                }
            }
        });

        Log.i(TAG, "All log files cleared");
    }

    /**
     * Export logs as JSON string
     */
    public String exportLogsAsJson() {
        // This would be implemented to read all log files and export as JSON
        // For now, return basic info
        return String.format("{\"logDirectory\":\"%s\",\"totalSize\":%d,\"timestamp\":%d}",
            getLogDirectoryPath(), getTotalLogSize(), System.currentTimeMillis());
    }

    /**
     * Clean up resources
     */
    public void cleanup() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
        Log.d(TAG, "DataLogger cleaned up");
    }
}
