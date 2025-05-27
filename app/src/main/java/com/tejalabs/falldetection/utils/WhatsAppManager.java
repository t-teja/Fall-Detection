package com.tejalabs.falldetection.utils;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * WhatsApp messaging utility for emergency notifications
 */
public class WhatsAppManager {
    
    private static final String TAG = "WhatsAppManager";
    
    // WhatsApp package names
    private static final String WHATSAPP_PACKAGE = "com.whatsapp";
    private static final String WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b";
    
    private Context context;
    
    public WhatsAppManager(Context context) {
        this.context = context;
    }
    
    /**
     * Check if WhatsApp is installed on the device
     */
    public boolean isWhatsAppInstalled() {
        PackageManager pm = context.getPackageManager();
        try {
            pm.getPackageInfo(WHATSAPP_PACKAGE, PackageManager.GET_ACTIVITIES);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            try {
                pm.getPackageInfo(WHATSAPP_BUSINESS_PACKAGE, PackageManager.GET_ACTIVITIES);
                return true;
            } catch (PackageManager.NameNotFoundException ex) {
                return false;
            }
        }
    }
    
    /**
     * Send emergency message via WhatsApp
     */
    public boolean sendEmergencyMessage(String phoneNumber, String message) {
        if (!isWhatsAppInstalled()) {
            Log.w(TAG, "WhatsApp is not installed");
            return false;
        }
        
        try {
            // Clean phone number (remove non-digits except +)
            String cleanNumber = phoneNumber.replaceAll("[^0-9+]", "");
            
            // Remove leading + if present for WhatsApp API
            if (cleanNumber.startsWith("+")) {
                cleanNumber = cleanNumber.substring(1);
            }
            
            // Encode message for URL
            String encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8.toString());
            
            // Create WhatsApp intent
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://api.whatsapp.com/send?phone=" + cleanNumber + "&text=" + encodedMessage));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            // Try to open WhatsApp
            context.startActivity(intent);
            
            Log.i(TAG, "WhatsApp message sent to: " + phoneNumber);
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error sending WhatsApp message", e);
            return false;
        }
    }
    
    /**
     * Send emergency message via WhatsApp with fallback to SMS
     */
    public boolean sendEmergencyMessageWithFallback(String phoneNumber, String message) {
        // Try WhatsApp first
        if (sendEmergencyMessage(phoneNumber, message)) {
            return true;
        }
        
        // Fallback to SMS if WhatsApp fails
        Log.i(TAG, "WhatsApp failed, falling back to SMS");
        return sendSMSFallback(phoneNumber, message);
    }
    
    /**
     * Fallback SMS sending
     */
    private boolean sendSMSFallback(String phoneNumber, String message) {
        try {
            Intent smsIntent = new Intent(Intent.ACTION_SENDTO);
            smsIntent.setData(Uri.parse("smsto:" + phoneNumber));
            smsIntent.putExtra("sms_body", message);
            smsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            context.startActivity(smsIntent);
            Log.i(TAG, "SMS fallback sent to: " + phoneNumber);
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error sending SMS fallback", e);
            return false;
        }
    }
    
    /**
     * Create emergency message with location
     */
    public static String createEmergencyMessage(String userName, double latitude, double longitude) {
        String googleMapsLink = createGoogleMapsLink(latitude, longitude);
        
        return String.format(
            "🚨 EMERGENCY ALERT 🚨\n\n" +
            "%s has experienced a fall and may need immediate assistance!\n\n" +
            "📍 Location: %s\n\n" +
            "⏰ Time: %s\n\n" +
            "Please check on them immediately or call emergency services if you cannot reach them.\n\n" +
            "This message was sent automatically by Fall Detection App.",
            userName,
            googleMapsLink,
            new java.text.SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", java.util.Locale.getDefault())
                .format(new java.util.Date())
        );
    }
    
    /**
     * Create Google Maps link from coordinates
     */
    public static String createGoogleMapsLink(double latitude, double longitude) {
        return String.format("https://maps.google.com/?q=%.6f,%.6f", latitude, longitude);
    }
    
    /**
     * Create emergency message without location
     */
    public static String createEmergencyMessageNoLocation(String userName) {
        return String.format(
            "🚨 EMERGENCY ALERT 🚨\n\n" +
            "%s has experienced a fall and may need immediate assistance!\n\n" +
            "📍 Location: Unable to determine location\n\n" +
            "⏰ Time: %s\n\n" +
            "Please check on them immediately or call emergency services if you cannot reach them.\n\n" +
            "This message was sent automatically by Fall Detection App.",
            userName,
            new java.text.SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", java.util.Locale.getDefault())
                .format(new java.util.Date())
        );
    }
    
    /**
     * Get WhatsApp status for display
     */
    public String getWhatsAppStatus() {
        if (isWhatsAppInstalled()) {
            return "✅ WhatsApp Available";
        } else {
            return "❌ WhatsApp Not Installed";
        }
    }
    
    /**
     * Open WhatsApp in Play Store for installation
     */
    public void openWhatsAppInPlayStore() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("market://details?id=" + WHATSAPP_PACKAGE));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            // Fallback to web browser
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://play.google.com/store/apps/details?id=" + WHATSAPP_PACKAGE));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }
}
