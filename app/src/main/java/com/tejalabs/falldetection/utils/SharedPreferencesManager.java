package com.tejalabs.falldetection.utils;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;

/**
 * Centralized manager for SharedPreferences operations
 * Handles all app settings and configuration storage
 */
public class SharedPreferencesManager {
    
    private static SharedPreferencesManager instance;
    private SharedPreferences sharedPreferences;
    private SharedPreferences.Editor editor;
    
    // Preference Keys
    public static final String KEY_FALL_DETECTION_ENABLED = "fall_detection_enabled";
    public static final String KEY_SENSITIVITY_LEVEL = "sensitivity_level";
    public static final String KEY_EMERGENCY_COUNTDOWN = "emergency_countdown";
    public static final String KEY_AUTO_CALL_ENABLED = "auto_call_enabled";
    public static final String KEY_LOCATION_SHARING_ENABLED = "location_sharing_enabled";
    public static final String KEY_FIRST_TIME_SETUP = "first_time_setup";
    public static final String KEY_SERVICE_RUNNING = "service_running";
    public static final String KEY_LAST_FALL_DETECTION = "last_fall_detection";
    public static final String KEY_TOTAL_FALLS_DETECTED = "total_falls_detected";
    public static final String KEY_FALSE_POSITIVES = "false_positives";
    
    // Default Values
    public static final boolean DEFAULT_FALL_DETECTION_ENABLED = true;
    public static final int DEFAULT_SENSITIVITY_LEVEL = 3; // Scale 1-5
    public static final int DEFAULT_EMERGENCY_COUNTDOWN = 30; // seconds
    public static final boolean DEFAULT_AUTO_CALL_ENABLED = false;
    public static final boolean DEFAULT_LOCATION_SHARING_ENABLED = true;
    public static final boolean DEFAULT_FIRST_TIME_SETUP = true;
    
    private SharedPreferencesManager(Context context) {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        editor = sharedPreferences.edit();
    }
    
    public static synchronized SharedPreferencesManager getInstance(Context context) {
        if (instance == null) {
            instance = new SharedPreferencesManager(context.getApplicationContext());
        }
        return instance;
    }
    
    // Fall Detection Settings
    public void setFallDetectionEnabled(boolean enabled) {
        editor.putBoolean(KEY_FALL_DETECTION_ENABLED, enabled).apply();
    }
    
    public boolean isFallDetectionEnabled() {
        return sharedPreferences.getBoolean(KEY_FALL_DETECTION_ENABLED, DEFAULT_FALL_DETECTION_ENABLED);
    }
    
    public void setSensitivityLevel(int level) {
        editor.putInt(KEY_SENSITIVITY_LEVEL, level).apply();
    }
    
    public int getSensitivityLevel() {
        return sharedPreferences.getInt(KEY_SENSITIVITY_LEVEL, DEFAULT_SENSITIVITY_LEVEL);
    }
    
    public void setEmergencyCountdown(int seconds) {
        editor.putInt(KEY_EMERGENCY_COUNTDOWN, seconds).apply();
    }
    
    public int getEmergencyCountdown() {
        return sharedPreferences.getInt(KEY_EMERGENCY_COUNTDOWN, DEFAULT_EMERGENCY_COUNTDOWN);
    }
    
    // Emergency Response Settings
    public void setAutoCallEnabled(boolean enabled) {
        editor.putBoolean(KEY_AUTO_CALL_ENABLED, enabled).apply();
    }
    
    public boolean isAutoCallEnabled() {
        return sharedPreferences.getBoolean(KEY_AUTO_CALL_ENABLED, DEFAULT_AUTO_CALL_ENABLED);
    }
    
    public void setLocationSharingEnabled(boolean enabled) {
        editor.putBoolean(KEY_LOCATION_SHARING_ENABLED, enabled).apply();
    }
    
    public boolean isLocationSharingEnabled() {
        return sharedPreferences.getBoolean(KEY_LOCATION_SHARING_ENABLED, DEFAULT_LOCATION_SHARING_ENABLED);
    }
    
    // App State Management
    public void setFirstTimeSetup(boolean isFirstTime) {
        editor.putBoolean(KEY_FIRST_TIME_SETUP, isFirstTime).apply();
    }
    
    public boolean isFirstTimeSetup() {
        return sharedPreferences.getBoolean(KEY_FIRST_TIME_SETUP, DEFAULT_FIRST_TIME_SETUP);
    }
    
    public void setServiceRunning(boolean running) {
        editor.putBoolean(KEY_SERVICE_RUNNING, running).apply();
    }
    
    public boolean isServiceRunning() {
        return sharedPreferences.getBoolean(KEY_SERVICE_RUNNING, false);
    }
    
    // Statistics and Logging
    public void setLastFallDetection(long timestamp) {
        editor.putLong(KEY_LAST_FALL_DETECTION, timestamp).apply();
    }
    
    public long getLastFallDetection() {
        return sharedPreferences.getLong(KEY_LAST_FALL_DETECTION, 0);
    }
    
    public void incrementTotalFallsDetected() {
        int current = getTotalFallsDetected();
        editor.putInt(KEY_TOTAL_FALLS_DETECTED, current + 1).apply();
    }
    
    public int getTotalFallsDetected() {
        return sharedPreferences.getInt(KEY_TOTAL_FALLS_DETECTED, 0);
    }
    
    public void incrementFalsePositives() {
        int current = getFalsePositives();
        editor.putInt(KEY_FALSE_POSITIVES, current + 1).apply();
    }
    
    public int getFalsePositives() {
        return sharedPreferences.getInt(KEY_FALSE_POSITIVES, 0);
    }
    
    // Utility Methods
    public void clearAllData() {
        editor.clear().apply();
    }
    
    public void resetStatistics() {
        editor.remove(KEY_TOTAL_FALLS_DETECTED)
              .remove(KEY_FALSE_POSITIVES)
              .remove(KEY_LAST_FALL_DETECTION)
              .apply();
    }
    
    // Get sensitivity threshold based on level (1-5)
    public float getSensitivityThreshold() {
        int level = getSensitivityLevel();
        switch (level) {
            case 1: return 2.5f; // Very Low
            case 2: return 2.0f; // Low
            case 3: return 1.5f; // Medium (default)
            case 4: return 1.0f; // High
            case 5: return 0.8f; // Very High
            default: return 1.5f;
        }
    }
    
    // Export settings as JSON string for backup
    public String exportSettings() {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"fall_detection_enabled\":").append(isFallDetectionEnabled()).append(",");
        json.append("\"sensitivity_level\":").append(getSensitivityLevel()).append(",");
        json.append("\"emergency_countdown\":").append(getEmergencyCountdown()).append(",");
        json.append("\"auto_call_enabled\":").append(isAutoCallEnabled()).append(",");
        json.append("\"location_sharing_enabled\":").append(isLocationSharingEnabled());
        json.append("}");
        return json.toString();
    }
}
