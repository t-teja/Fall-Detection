package com.tejalabs.falldetection.utils;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

/**
 * Handles location services for emergency response
 * Provides current location for emergency alerts and SMS messages
 */
public class LocationService {
    
    private static final String TAG = "LocationService";
    
    // Location request parameters
    private static final long LOCATION_UPDATE_INTERVAL = 10000; // 10 seconds
    private static final long FASTEST_UPDATE_INTERVAL = 5000;   // 5 seconds
    private static final float MIN_DISTANCE_CHANGE = 10.0f;     // 10 meters
    private static final long LOCATION_TIMEOUT = 30000;        // 30 seconds timeout
    
    private Context context;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationManager locationManager;
    private Handler timeoutHandler;
    
    // Current location tracking
    private Location lastKnownLocation;
    private long lastLocationTime = 0;
    private boolean isLocationUpdatesActive = false;
    
    // Callback interfaces
    public interface LocationCallback {
        void onLocationReceived(Location location);
        void onLocationError(String error);
    }
    
    private LocationCallback currentLocationCallback;
    private com.google.android.gms.location.LocationCallback fusedLocationCallback;
    private LocationListener systemLocationListener;
    
    public LocationService(Context context) {
        this.context = context.getApplicationContext();
        this.fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
        this.locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        this.timeoutHandler = new Handler(Looper.getMainLooper());
        
        initializeLocationCallbacks();
        Log.d(TAG, "LocationService initialized");
    }
    
    /**
     * Initialize location callbacks
     */
    private void initializeLocationCallbacks() {
        // Fused Location Provider callback
        fusedLocationCallback = new com.google.android.gms.location.LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }
                
                Location location = locationResult.getLastLocation();
                if (location != null) {
                    updateLastKnownLocation(location);
                    
                    if (currentLocationCallback != null) {
                        currentLocationCallback.onLocationReceived(location);
                        currentLocationCallback = null;
                        stopLocationUpdates();
                    }
                }
            }
        };
        
        // System LocationManager callback
        systemLocationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                updateLastKnownLocation(location);
                
                if (currentLocationCallback != null) {
                    currentLocationCallback.onLocationReceived(location);
                    currentLocationCallback = null;
                    stopLocationUpdates();
                }
            }
            
            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {}
            
            @Override
            public void onProviderEnabled(String provider) {
                Log.d(TAG, "Location provider enabled: " + provider);
            }
            
            @Override
            public void onProviderDisabled(String provider) {
                Log.d(TAG, "Location provider disabled: " + provider);
            }
        };
    }
    
    /**
     * Get current location with callback
     */
    public void getCurrentLocation(LocationCallback callback) {
        if (callback == null) {
            Log.w(TAG, "Location callback is null");
            return;
        }
        
        if (!hasLocationPermission()) {
            callback.onLocationError("Location permission not granted");
            return;
        }
        
        // Check if we have a recent location (less than 5 minutes old)
        if (lastKnownLocation != null && 
            (System.currentTimeMillis() - lastLocationTime) < 300000) {
            Log.d(TAG, "Using cached location");
            callback.onLocationReceived(lastKnownLocation);
            return;
        }
        
        this.currentLocationCallback = callback;
        
        // Set timeout for location request
        timeoutHandler.postDelayed(() -> {
            if (currentLocationCallback != null) {
                Log.w(TAG, "Location request timed out");
                stopLocationUpdates();
                
                if (lastKnownLocation != null) {
                    currentLocationCallback.onLocationReceived(lastKnownLocation);
                } else {
                    currentLocationCallback.onLocationError("Location request timed out");
                }
                currentLocationCallback = null;
            }
        }, LOCATION_TIMEOUT);
        
        // Try to get location using Fused Location Provider first
        requestLocationWithFusedProvider();
    }
    
    /**
     * Request location using Fused Location Provider
     */
    private void requestLocationWithFusedProvider() {
        try {
            // First try to get last known location
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(location -> {
                        if (location != null && 
                            (System.currentTimeMillis() - location.getTime()) < 300000) {
                            // Location is recent enough
                            updateLastKnownLocation(location);
                            
                            if (currentLocationCallback != null) {
                                currentLocationCallback.onLocationReceived(location);
                                currentLocationCallback = null;
                                timeoutHandler.removeCallbacksAndMessages(null);
                            }
                            return;
                        }
                        
                        // Request fresh location
                        requestFreshLocation();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to get last known location", e);
                        requestFreshLocation();
                    });
            } else {
                requestFreshLocation();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error requesting location with Fused Provider", e);
            requestLocationWithSystemProvider();
        }
    }
    
    /**
     * Request fresh location updates
     */
    private void requestFreshLocation() {
        try {
            LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_UPDATE_INTERVAL)
                .setWaitForAccurateLocation(false)
                .setMinUpdateIntervalMillis(FASTEST_UPDATE_INTERVAL)
                .setMaxUpdateDelayMillis(LOCATION_UPDATE_INTERVAL)
                .build();
            
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                fusedLocationClient.requestLocationUpdates(locationRequest, fusedLocationCallback, Looper.getMainLooper());
                isLocationUpdatesActive = true;
                Log.d(TAG, "Requesting fresh location with Fused Provider");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error requesting fresh location", e);
            requestLocationWithSystemProvider();
        }
    }
    
    /**
     * Fallback to system LocationManager
     */
    private void requestLocationWithSystemProvider() {
        try {
            if (!hasLocationPermission()) {
                if (currentLocationCallback != null) {
                    currentLocationCallback.onLocationError("Location permission not granted");
                    currentLocationCallback = null;
                }
                return;
            }
            
            boolean isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            boolean isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            
            if (!isGPSEnabled && !isNetworkEnabled) {
                if (currentLocationCallback != null) {
                    currentLocationCallback.onLocationError("Location services are disabled");
                    currentLocationCallback = null;
                }
                return;
            }
            
            // Try GPS first, then Network
            String provider = isGPSEnabled ? LocationManager.GPS_PROVIDER : LocationManager.NETWORK_PROVIDER;
            
            locationManager.requestSingleUpdate(provider, systemLocationListener, Looper.getMainLooper());
            isLocationUpdatesActive = true;
            
            Log.d(TAG, "Requesting location with system provider: " + provider);
            
        } catch (Exception e) {
            Log.e(TAG, "Error requesting location with system provider", e);
            if (currentLocationCallback != null) {
                currentLocationCallback.onLocationError("Failed to request location: " + e.getMessage());
                currentLocationCallback = null;
            }
        }
    }
    
    /**
     * Stop location updates
     */
    private void stopLocationUpdates() {
        if (!isLocationUpdatesActive) {
            return;
        }
        
        try {
            // Stop Fused Location Provider updates
            fusedLocationClient.removeLocationUpdates(fusedLocationCallback);
            
            // Stop system LocationManager updates
            locationManager.removeUpdates(systemLocationListener);
            
            isLocationUpdatesActive = false;
            timeoutHandler.removeCallbacksAndMessages(null);
            
            Log.d(TAG, "Location updates stopped");
            
        } catch (Exception e) {
            Log.e(TAG, "Error stopping location updates", e);
        }
    }
    
    /**
     * Update last known location
     */
    private void updateLastKnownLocation(Location location) {
        if (location != null) {
            lastKnownLocation = location;
            lastLocationTime = System.currentTimeMillis();
            
            Log.d(TAG, String.format("Location updated: %.6f, %.6f (accuracy: %.1fm)",
                location.getLatitude(), location.getLongitude(), location.getAccuracy()));
        }
    }
    
    /**
     * Check if location permissions are granted
     */
    private boolean hasLocationPermission() {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
               ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
    
    /**
     * Get last known location (may be null or outdated)
     */
    public Location getLastKnownLocation() {
        return lastKnownLocation;
    }
    
    /**
     * Check if location services are available
     */
    public boolean isLocationAvailable() {
        if (!hasLocationPermission()) {
            return false;
        }
        
        boolean isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        
        return isGPSEnabled || isNetworkEnabled;
    }
    
    /**
     * Get location status information
     */
    public String getLocationStatus() {
        if (!hasLocationPermission()) {
            return "Location permission not granted";
        }
        
        boolean isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        
        if (!isGPSEnabled && !isNetworkEnabled) {
            return "Location services disabled";
        }
        
        if (lastKnownLocation != null) {
            long ageMinutes = (System.currentTimeMillis() - lastLocationTime) / 60000;
            return String.format("Last location: %d minutes ago (accuracy: %.1fm)",
                ageMinutes, lastKnownLocation.getAccuracy());
        }
        
        return "Location available, no recent fix";
    }
    
    /**
     * Clean up resources
     */
    public void cleanup() {
        stopLocationUpdates();
        currentLocationCallback = null;
        timeoutHandler.removeCallbacksAndMessages(null);
        Log.d(TAG, "LocationService cleaned up");
    }
}
