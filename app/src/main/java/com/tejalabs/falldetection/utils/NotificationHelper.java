package com.tejalabs.falldetection.utils;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.tejalabs.falldetection.MainActivity;
import com.tejalabs.falldetection.R;

/**
 * Helper class for managing all app notifications
 * Handles foreground service notifications, emergency alerts, and status updates
 */
public class NotificationHelper {
    
    private static NotificationHelper instance;
    private Context context;
    private NotificationManager notificationManager;
    
    // Notification Channels
    public static final String CHANNEL_FOREGROUND_SERVICE = "fall_detection_service";
    public static final String CHANNEL_EMERGENCY_ALERT = "emergency_alert";
    public static final String CHANNEL_STATUS_UPDATE = "status_update";
    
    // Notification IDs
    public static final int NOTIFICATION_ID_FOREGROUND_SERVICE = 1001;
    public static final int NOTIFICATION_ID_EMERGENCY_ALERT = 1002;
    public static final int NOTIFICATION_ID_FALL_DETECTED = 1003;
    public static final int NOTIFICATION_ID_STATUS_UPDATE = 1004;
    
    private NotificationHelper(Context context) {
        this.context = context.getApplicationContext();
        this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannels();
    }
    
    public static synchronized NotificationHelper getInstance(Context context) {
        if (instance == null) {
            instance = new NotificationHelper(context);
        }
        return instance;
    }
    
    /**
     * Create notification channels for Android O and above
     */
    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Foreground Service Channel
            NotificationChannel serviceChannel = new NotificationChannel(
                CHANNEL_FOREGROUND_SERVICE,
                "Fall Detection Service",
                NotificationManager.IMPORTANCE_LOW
            );
            serviceChannel.setDescription("Ongoing fall detection monitoring");
            serviceChannel.setShowBadge(false);
            serviceChannel.setSound(null, null);
            
            // Emergency Alert Channel
            NotificationChannel emergencyChannel = new NotificationChannel(
                CHANNEL_EMERGENCY_ALERT,
                "Emergency Alerts",
                NotificationManager.IMPORTANCE_HIGH
            );
            emergencyChannel.setDescription("Critical fall detection alerts");
            emergencyChannel.setShowBadge(true);
            emergencyChannel.enableVibration(true);
            emergencyChannel.enableLights(true);
            
            // Status Update Channel
            NotificationChannel statusChannel = new NotificationChannel(
                CHANNEL_STATUS_UPDATE,
                "Status Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            );
            statusChannel.setDescription("App status and information updates");
            statusChannel.setShowBadge(false);
            
            notificationManager.createNotificationChannel(serviceChannel);
            notificationManager.createNotificationChannel(emergencyChannel);
            notificationManager.createNotificationChannel(statusChannel);
        }
    }
    
    /**
     * Create foreground service notification
     */
    public Notification createForegroundServiceNotification(boolean isMonitoring) {
        Intent notificationIntent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            context, 0, notificationIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        String title = "Fall Detection Active";
        String content = isMonitoring ? 
            "Monitoring for falls in background" : 
            "Service running - monitoring paused";
        
        return new NotificationCompat.Builder(context, CHANNEL_FOREGROUND_SERVICE)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_notification_fall_detection)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setShowWhen(false)
            .build();
    }
    
    /**
     * Show emergency alert notification
     */
    public void showEmergencyAlert(String message, boolean isCritical) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            context, 0, intent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_EMERGENCY_ALERT)
            .setContentTitle("FALL DETECTED!")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_notification_emergency)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setVibrate(new long[]{0, 1000, 500, 1000})
            .setLights(0xFFFF0000, 1000, 500);
        
        if (isCritical) {
            builder.setFullScreenIntent(pendingIntent, true);
        }
        
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_EMERGENCY_ALERT, builder.build());
    }
    
    /**
     * Show fall detected notification with countdown
     */
    public void showFallDetectedNotification(int countdownSeconds) {
        Intent cancelIntent = new Intent("com.tejalabs.falldetection.CANCEL_EMERGENCY");
        PendingIntent cancelPendingIntent = PendingIntent.getBroadcast(
            context, 0, cancelIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        String message = String.format("Emergency alert in %d seconds. Tap to cancel.", countdownSeconds);
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_EMERGENCY_ALERT)
            .setContentTitle("Fall Detected!")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_notification_warning)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(R.drawable.ic_cancel, "Cancel Alert", cancelPendingIntent)
            .setProgress(countdownSeconds, countdownSeconds, false)
            .setVibrate(new long[]{0, 500, 250, 500})
            .setLights(0xFFFFAA00, 500, 500);
        
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_FALL_DETECTED, builder.build());
    }
    
    /**
     * Update fall detected notification countdown
     */
    public void updateFallDetectedCountdown(int remainingSeconds) {
        String message = String.format("Emergency alert in %d seconds. Tap to cancel.", remainingSeconds);
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_EMERGENCY_ALERT)
            .setContentTitle("Fall Detected!")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_notification_warning)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setProgress(30, remainingSeconds, false); // Assuming 30 second countdown
        
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_FALL_DETECTED, builder.build());
    }
    
    /**
     * Show status update notification
     */
    public void showStatusUpdate(String title, String message) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_STATUS_UPDATE)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_notification_info)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true);
        
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_STATUS_UPDATE, builder.build());
    }
    
    /**
     * Cancel specific notification
     */
    public void cancelNotification(int notificationId) {
        notificationManager.cancel(notificationId);
    }
    
    /**
     * Cancel all notifications
     */
    public void cancelAllNotifications() {
        notificationManager.cancelAll();
    }
    
    /**
     * Update foreground service notification
     */
    public void updateForegroundServiceNotification(String status) {
        Notification notification = new NotificationCompat.Builder(context, CHANNEL_FOREGROUND_SERVICE)
            .setContentTitle("Fall Detection Active")
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_notification_fall_detection)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
        
        notificationManager.notify(NOTIFICATION_ID_FOREGROUND_SERVICE, notification);
    }
}
