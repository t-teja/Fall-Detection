package com.tejalabs.falldetection.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.tejalabs.falldetection.services.FallDetectionService;
import com.tejalabs.falldetection.utils.DataLogger;
import com.tejalabs.falldetection.utils.SharedPreferencesManager;

/**
 * Broadcast receiver to automatically start fall detection service on device boot
 * Ensures continuous monitoring for elderly users
 */
public class BootReceiver extends BroadcastReceiver {
    
    private static final String TAG = "BootReceiver";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        
        Log.d(TAG, "Boot receiver triggered with action: " + action);
        
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
            Intent.ACTION_MY_PACKAGE_REPLACED.equals(action) ||
            Intent.ACTION_PACKAGE_REPLACED.equals(action)) {
            
            handleBootCompleted(context);
        }
    }
    
    /**
     * Handle device boot completion
     */
    private void handleBootCompleted(Context context) {
        Log.i(TAG, "Device boot completed - checking fall detection service");
        
        SharedPreferencesManager prefsManager = SharedPreferencesManager.getInstance(context);
        DataLogger dataLogger = DataLogger.getInstance(context);
        
        // Log boot event
        dataLogger.logSystemEvent("BOOT", "Device boot completed", "Auto-start check");
        
        // Check if fall detection should be auto-started
        boolean shouldAutoStart = shouldAutoStartService(prefsManager);
        
        if (shouldAutoStart) {
            startFallDetectionService(context, dataLogger);
        } else {
            Log.i(TAG, "Auto-start disabled or first-time setup required");
            dataLogger.logSystemEvent("BOOT", "Auto-start skipped", 
                "Fall detection disabled or first-time setup required");
        }
    }
    
    /**
     * Determine if service should auto-start
     */
    private boolean shouldAutoStartService(SharedPreferencesManager prefsManager) {
        // Don't auto-start if it's first time setup
        if (prefsManager.isFirstTimeSetup()) {
            Log.d(TAG, "First time setup required - skipping auto-start");
            return false;
        }
        
        // Don't auto-start if fall detection is disabled
        if (!prefsManager.isFallDetectionEnabled()) {
            Log.d(TAG, "Fall detection disabled - skipping auto-start");
            return false;
        }
        
        // Check if service was running before reboot
        boolean wasRunning = prefsManager.isServiceRunning();
        Log.d(TAG, "Service was running before reboot: " + wasRunning);
        
        return wasRunning;
    }
    
    /**
     * Start the fall detection service
     */
    private void startFallDetectionService(Context context, DataLogger dataLogger) {
        try {
            Log.i(TAG, "Starting Fall Detection Service on boot");
            
            Intent serviceIntent = new Intent(context, FallDetectionService.class);
            serviceIntent.setAction(FallDetectionService.ACTION_START_MONITORING);
            
            // Start as foreground service
            context.startForegroundService(serviceIntent);
            
            dataLogger.logSystemEvent("BOOT", "Service auto-started", "Fall detection service started on boot");
            
            Log.i(TAG, "Fall Detection Service started successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to start Fall Detection Service on boot", e);
            dataLogger.logError("BootReceiver", "Failed to start service on boot", e);
        }
    }
}
