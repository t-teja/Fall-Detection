package com.tejalabs.falldetection.utils;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SmsManager;
import android.util.Log;
import androidx.core.app.ActivityCompat;
import com.tejalabs.falldetection.models.EmergencyContact;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Manages emergency response procedures when a fall is detected
 * Handles countdown timer, SMS alerts, emergency calls, and location sharing
 */
public class EmergencyManager {

    private static final String TAG = "EmergencyManager";

    private Context context;
    private SharedPreferencesManager prefsManager;
    private ContactManager contactManager;
    private NotificationHelper notificationHelper;
    private LocationService locationService;
    private SoundManager soundManager;

    // Emergency countdown
    private Handler countdownHandler;
    private Runnable countdownRunnable;
    private boolean isCountdownActive = false;
    private int remainingSeconds = 0;

    // Emergency state
    private boolean isEmergencyActive = false;
    private long emergencyStartTime = 0;

    // Listeners
    private EmergencyListener emergencyListener;

    // Learning data for false alarms
    private SensorDataCollector.SensorDataWindow lastDetectionData;
    private float lastDetectionConfidence;

    public interface EmergencyListener {
        void onCountdownStarted(int seconds);
        void onCountdownTick(int remainingSeconds);
        void onCountdownCancelled();
        void onEmergencyActivated();
        void onEmergencyCompleted();
        void onFalseAlarmLearning(SensorDataCollector.SensorDataWindow sensorData, float confidence);
    }

    public EmergencyManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefsManager = SharedPreferencesManager.getInstance(context);
        this.contactManager = new ContactManager(context);
        this.notificationHelper = NotificationHelper.getInstance(context);
        this.locationService = new LocationService(context);
        this.soundManager = SoundManager.getInstance(context);
        this.countdownHandler = new Handler(Looper.getMainLooper());

        Log.d(TAG, "EmergencyManager initialized");
    }

    public void setEmergencyListener(EmergencyListener listener) {
        this.emergencyListener = listener;
    }

    /**
     * Start emergency response procedure with countdown
     */
    public void startEmergencyResponse(float fallConfidence) {
        startEmergencyResponse(fallConfidence, null);
    }

    /**
     * Start emergency response procedure with countdown and sensor data for learning
     */
    public void startEmergencyResponse(float fallConfidence, SensorDataCollector.SensorDataWindow sensorData) {
        if (isCountdownActive || isEmergencyActive) {
            Log.w(TAG, "Emergency response already in progress");
            return;
        }

        Log.i(TAG, "Starting emergency response - Fall confidence: " + fallConfidence);

        // Store detection data for potential learning
        lastDetectionData = sensorData;
        lastDetectionConfidence = fallConfidence;

        // Get countdown duration from settings
        remainingSeconds = prefsManager.getEmergencyCountdown();
        isCountdownActive = true;
        emergencyStartTime = System.currentTimeMillis();

        // Update statistics
        prefsManager.incrementTotalFallsDetected();
        prefsManager.setLastFallDetection(emergencyStartTime);

        // Show countdown notification
        notificationHelper.showFallDetectedNotification(remainingSeconds);

        // Notify listener
        if (emergencyListener != null) {
            emergencyListener.onCountdownStarted(remainingSeconds);
        }

        // Start countdown
        startCountdown();
    }

    /**
     * Cancel emergency response (false positive)
     */
    public void cancelEmergencyResponse() {
        if (!isCountdownActive && !isEmergencyActive) {
            Log.w(TAG, "No emergency response to cancel");
            return;
        }

        Log.i(TAG, "Emergency response cancelled by user");

        // Stop countdown
        if (isCountdownActive) {
            stopCountdown();
            prefsManager.incrementFalsePositives();

            // Trigger learning from false alarm
            if (lastDetectionData != null && emergencyListener != null) {
                emergencyListener.onFalseAlarmLearning(lastDetectionData, lastDetectionConfidence);
                Log.i(TAG, "Triggering TinyML learning from false alarm");
            }
        }

        // Cancel notifications
        notificationHelper.cancelNotification(NotificationHelper.NOTIFICATION_ID_FALL_DETECTED);
        notificationHelper.cancelNotification(NotificationHelper.NOTIFICATION_ID_EMERGENCY_ALERT);

        // Reset state
        isCountdownActive = false;
        isEmergencyActive = false;

        // Notify listener
        if (emergencyListener != null) {
            emergencyListener.onCountdownCancelled();
        }

        // Show cancellation status
        notificationHelper.showStatusUpdate("Emergency Cancelled", "Fall detection alert was cancelled");
    }

    /**
     * Activate emergency immediately (user confirmed)
     */
    public void activateEmergencyImmediately() {
        Log.i(TAG, "Emergency activated immediately by user confirmation");

        // Stop any active countdown
        if (isCountdownActive) {
            stopCountdown();
        }

        // Activate emergency procedures directly
        activateEmergency();
    }

    /**
     * Start the countdown timer
     */
    private void startCountdown() {
        countdownRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isCountdownActive) {
                    return;
                }

                remainingSeconds--;

                // Update notification
                notificationHelper.updateFallDetectedCountdown(remainingSeconds);

                // Notify listener
                if (emergencyListener != null) {
                    emergencyListener.onCountdownTick(remainingSeconds);
                }

                if (remainingSeconds <= 0) {
                    // Countdown finished - activate emergency
                    activateEmergency();
                } else {
                    // Continue countdown
                    countdownHandler.postDelayed(this, 1000);
                }
            }
        };

        countdownHandler.postDelayed(countdownRunnable, 1000);
    }

    /**
     * Stop the countdown timer
     */
    private void stopCountdown() {
        if (countdownRunnable != null) {
            countdownHandler.removeCallbacks(countdownRunnable);
            countdownRunnable = null;
        }
        isCountdownActive = false;
    }

    /**
     * Activate emergency procedures
     */
    private void activateEmergency() {
        Log.i(TAG, "Activating emergency procedures");

        stopCountdown();
        isEmergencyActive = true;

        // Cancel countdown notification
        notificationHelper.cancelNotification(NotificationHelper.NOTIFICATION_ID_FALL_DETECTED);

        // Play emergency siren to alert nearby people
        soundManager.playEmergencySiren();

        // Show emergency alert
        notificationHelper.showEmergencyAlert("Emergency procedures activated! Siren playing to alert nearby people.", true);

        // Notify listener
        if (emergencyListener != null) {
            emergencyListener.onEmergencyActivated();
        }

        // Execute emergency procedures
        executeEmergencyProcedures();
    }

    /**
     * Execute all emergency procedures
     */
    private void executeEmergencyProcedures() {
        // Get current location
        locationService.getCurrentLocation(new LocationService.LocationCallback() {
            @Override
            public void onLocationReceived(Location location) {
                String locationText = formatLocationText(location);

                // Send SMS alerts
                sendEmergencySMS(locationText);

                // Make emergency call if enabled
                if (prefsManager.isAutoCallEnabled()) {
                    makeEmergencyCall();
                }

                // Complete emergency response
                completeEmergencyResponse();
            }

            @Override
            public void onLocationError(String error) {
                Log.e(TAG, "Location error during emergency: " + error);

                // Send SMS without location
                sendEmergencySMS("Location unavailable");

                // Make emergency call if enabled
                if (prefsManager.isAutoCallEnabled()) {
                    makeEmergencyCall();
                }

                // Complete emergency response
                completeEmergencyResponse();
            }
        });
    }

    /**
     * Send emergency SMS to all contacts
     */
    private void sendEmergencySMS(String locationText) {
        if (!hasPermission(Manifest.permission.SEND_SMS)) {
            Log.e(TAG, "SMS permission not granted");
            return;
        }

        List<EmergencyContact> contacts = contactManager.getEmergencyContacts();
        if (contacts.isEmpty()) {
            Log.w(TAG, "No emergency contacts configured");
            return;
        }

        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(new Date());

        String message = String.format(
            "EMERGENCY ALERT: Fall detected at %s. Location: %s. Please check on me immediately.",
            timestamp, locationText
        );

        SmsManager smsManager = SmsManager.getDefault();

        for (EmergencyContact contact : contacts) {
            try {
                smsManager.sendTextMessage(contact.getPhoneNumber(), null, message, null, null);
                Log.i(TAG, "Emergency SMS sent to: " + contact.getName());
            } catch (Exception e) {
                Log.e(TAG, "Failed to send SMS to " + contact.getName(), e);
            }
        }

        notificationHelper.showStatusUpdate("Emergency SMS Sent",
            "Alerts sent to " + contacts.size() + " emergency contacts");
    }

    /**
     * Make emergency call to primary contact
     */
    private void makeEmergencyCall() {
        if (!hasPermission(Manifest.permission.CALL_PHONE)) {
            Log.e(TAG, "Call permission not granted");
            return;
        }

        EmergencyContact primaryContact = contactManager.getPrimaryEmergencyContact();
        if (primaryContact == null) {
            Log.w(TAG, "No primary emergency contact configured");
            return;
        }

        try {
            Intent callIntent = new Intent(Intent.ACTION_CALL);
            callIntent.setData(Uri.parse("tel:" + primaryContact.getPhoneNumber()));
            callIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(callIntent);

            Log.i(TAG, "Emergency call initiated to: " + primaryContact.getName());

            notificationHelper.showStatusUpdate("Emergency Call",
                "Calling " + primaryContact.getName());

        } catch (Exception e) {
            Log.e(TAG, "Failed to make emergency call", e);
        }
    }

    /**
     * Format location information for SMS
     */
    private String formatLocationText(Location location) {
        if (location == null) {
            return "Location unavailable";
        }

        return String.format(Locale.getDefault(),
            "Lat: %.6f, Lon: %.6f (Accuracy: %.0fm)",
            location.getLatitude(),
            location.getLongitude(),
            location.getAccuracy()
        );
    }

    /**
     * Complete emergency response procedures
     */
    private void completeEmergencyResponse() {
        Log.i(TAG, "Emergency response procedures completed");

        isEmergencyActive = false;

        // Notify listener
        if (emergencyListener != null) {
            emergencyListener.onEmergencyCompleted();
        }

        // Log emergency event
        DataLogger.getInstance(context).logEmergencyEvent(
            emergencyStartTime,
            System.currentTimeMillis(),
            "Emergency response completed"
        );
    }

    /**
     * Check if permission is granted
     */
    private boolean hasPermission(String permission) {
        return ActivityCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Get emergency status
     */
    public boolean isEmergencyActive() {
        return isEmergencyActive;
    }

    public boolean isCountdownActive() {
        return isCountdownActive;
    }

    public int getRemainingSeconds() {
        return remainingSeconds;
    }

    /**
     * Test emergency procedures (for testing purposes)
     */
    public void testEmergencyProcedures() {
        Log.i(TAG, "Testing emergency procedures");

        // Send test SMS
        List<EmergencyContact> contacts = contactManager.getEmergencyContacts();
        if (!contacts.isEmpty() && hasPermission(Manifest.permission.SEND_SMS)) {
            String testMessage = "This is a test of the fall detection emergency system. Please ignore.";
            SmsManager smsManager = SmsManager.getDefault();

            try {
                smsManager.sendTextMessage(contacts.get(0).getPhoneNumber(), null, testMessage, null, null);
                notificationHelper.showStatusUpdate("Test SMS Sent", "Emergency system test completed");
            } catch (Exception e) {
                Log.e(TAG, "Failed to send test SMS", e);
            }
        }
    }

    /**
     * Clean up resources
     */
    public void cleanup() {
        stopCountdown();
        isEmergencyActive = false;
        isCountdownActive = false;

        if (locationService != null) {
            locationService.cleanup();
        }

        if (soundManager != null) {
            soundManager.cleanup();
        }

        Log.d(TAG, "EmergencyManager cleaned up");
    }
}
