package com.tejalabs.falldetection;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.tejalabs.falldetection.activities.EmergencyContactsActivity;
import com.tejalabs.falldetection.activities.SettingsActivity;
import com.tejalabs.falldetection.services.FallDetectionService;
import com.tejalabs.falldetection.utils.ContactManager;
import com.tejalabs.falldetection.utils.DataLogger;
import com.tejalabs.falldetection.utils.SharedPreferencesManager;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements FallDetectionService.ServiceListener {

    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 1001;

    // UI Components
    private Button btnStartStop;
    private Button btnSettings;
    private Button btnEmergencyContacts;
    private Button btnTestEmergency;
    private TextView tvServiceStatus;
    private TextView tvMonitoringStatus;
    private TextView tvEmergencyStatus;
    private TextView tvContactsStatus;

    // Service connection
    private FallDetectionService fallDetectionService;
    private boolean isServiceBound = false;

    // Utilities
    private SharedPreferencesManager prefsManager;
    private ContactManager contactManager;
    private DataLogger dataLogger;

    // Required permissions
    private static final String[] REQUIRED_PERMISSIONS = {
        Manifest.permission.BODY_SENSORS,
        Manifest.permission.ACTIVITY_RECOGNITION,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.SEND_SMS,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.POST_NOTIFICATIONS,
        Manifest.permission.FOREGROUND_SERVICE,
        Manifest.permission.WAKE_LOCK
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG, "MainActivity created");

        // Initialize utilities
        initializeUtilities();

        // Initialize UI
        initializeUI();

        // Check permissions
        checkAndRequestPermissions();

        // Bind to service
        bindToFallDetectionService();

        // Log app start
        dataLogger.logSystemEvent("APP", "MainActivity created", "App started");
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUI();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Unbind from service
        if (isServiceBound) {
            unbindService(serviceConnection);
            isServiceBound = false;
        }

        Log.d(TAG, "MainActivity destroyed");
    }

    /**
     * Initialize utility classes
     */
    private void initializeUtilities() {
        prefsManager = SharedPreferencesManager.getInstance(this);
        contactManager = new ContactManager(this);
        dataLogger = DataLogger.getInstance(this);
    }

    /**
     * Initialize UI components
     */
    private void initializeUI() {
        // Find views
        btnStartStop = findViewById(R.id.btn_start_stop);
        btnSettings = findViewById(R.id.btn_settings);
        btnEmergencyContacts = findViewById(R.id.btn_emergency_contacts);
        btnTestEmergency = findViewById(R.id.btn_test_emergency);
        tvServiceStatus = findViewById(R.id.tv_service_status);
        tvMonitoringStatus = findViewById(R.id.tv_monitoring_status);
        tvEmergencyStatus = findViewById(R.id.tv_emergency_status);
        tvContactsStatus = findViewById(R.id.tv_contacts_status);

        // Set click listeners
        btnStartStop.setOnClickListener(this::onStartStopClicked);
        btnSettings.setOnClickListener(this::onSettingsClicked);
        btnEmergencyContacts.setOnClickListener(this::onEmergencyContactsClicked);
        btnTestEmergency.setOnClickListener(this::onTestEmergencyClicked);

        // Initial UI update
        updateUI();
    }

    /**
     * Check and request required permissions
     */
    private void checkAndRequestPermissions() {
        List<String> missingPermissions = new ArrayList<>();

        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }

        if (!missingPermissions.isEmpty()) {
            Log.d(TAG, "Requesting " + missingPermissions.size() + " permissions");
            ActivityCompat.requestPermissions(this,
                missingPermissions.toArray(new String[0]),
                PERMISSION_REQUEST_CODE);
        } else {
            Log.d(TAG, "All permissions granted");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            int grantedCount = 0;

            for (int i = 0; i < permissions.length; i++) {
                boolean granted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
                dataLogger.logPermissionEvent(permissions[i], granted);

                if (granted) {
                    grantedCount++;
                } else {
                    Log.w(TAG, "Permission denied: " + permissions[i]);
                }
            }

            if (grantedCount == permissions.length) {
                Toast.makeText(this, "All permissions granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Some permissions were denied. App may not work properly.",
                    Toast.LENGTH_LONG).show();
            }

            updateUI();
        }
    }

    /**
     * Bind to fall detection service
     */
    private void bindToFallDetectionService() {
        Intent intent = new Intent(this, FallDetectionService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    /**
     * Service connection callback
     */
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "Service connected");

            FallDetectionService.FallDetectionBinder binder =
                (FallDetectionService.FallDetectionBinder) service;
            fallDetectionService = binder.getService();
            fallDetectionService.setServiceListener(MainActivity.this);
            isServiceBound = true;

            updateUI();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "Service disconnected");
            fallDetectionService = null;
            isServiceBound = false;
            updateUI();
        }
    };

    /**
     * Update UI based on current state
     */
    private void updateUI() {
        runOnUiThread(() -> {
            // Update service status
            if (isServiceBound && fallDetectionService != null) {
                tvServiceStatus.setText("Service: " + fallDetectionService.getServiceStatus());

                boolean isMonitoring = fallDetectionService.isMonitoring();
                tvMonitoringStatus.setText("Monitoring: " + (isMonitoring ? "Active" : "Inactive"));

                btnStartStop.setText(isMonitoring ? "Stop Monitoring" : "Start Monitoring");
                btnStartStop.setEnabled(true);

            } else {
                tvServiceStatus.setText("Service: Not connected");
                tvMonitoringStatus.setText("Monitoring: Unknown");
                btnStartStop.setText("Start Service");
                btnStartStop.setEnabled(true);
            }

            // Update emergency status
            if (isServiceBound && fallDetectionService != null) {
                boolean isEmergencyActive = fallDetectionService.getEmergencyManager().isEmergencyActive();
                tvEmergencyStatus.setText("Emergency: " + (isEmergencyActive ? "ACTIVE" : "None"));
            } else {
                tvEmergencyStatus.setText("Emergency: Unknown");
            }

            // Update contacts status
            int contactCount = contactManager.getContactCount();
            tvContactsStatus.setText("Emergency Contacts: " + contactCount + " configured");

            // Enable/disable test button based on contacts
            btnTestEmergency.setEnabled(contactCount > 0);
        });
    }

    /**
     * Handle start/stop button click
     */
    private void onStartStopClicked(View view) {
        if (!isServiceBound || fallDetectionService == null) {
            // Start service
            startFallDetectionService();
        } else {
            // Toggle monitoring
            if (fallDetectionService.isMonitoring()) {
                fallDetectionService.stopMonitoring();
                Toast.makeText(this, "Fall detection stopped", Toast.LENGTH_SHORT).show();
            } else {
                if (contactManager.getContactCount() == 0) {
                    Toast.makeText(this, "Please add emergency contacts first", Toast.LENGTH_LONG).show();
                    onEmergencyContactsClicked(view);
                    return;
                }

                fallDetectionService.startMonitoring();
                Toast.makeText(this, "Fall detection started", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Start fall detection service
     */
    private void startFallDetectionService() {
        Intent intent = new Intent(this, FallDetectionService.class);
        intent.setAction(FallDetectionService.ACTION_START_MONITORING);
        startForegroundService(intent);

        // Bind to service
        bindToFallDetectionService();

        Toast.makeText(this, "Starting fall detection service...", Toast.LENGTH_SHORT).show();
    }

    /**
     * Handle settings button click
     */
    private void onSettingsClicked(View view) {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    /**
     * Handle emergency contacts button click
     */
    private void onEmergencyContactsClicked(View view) {
        Intent intent = new Intent(this, EmergencyContactsActivity.class);
        startActivity(intent);
    }

    /**
     * Handle test emergency button click
     */
    private void onTestEmergencyClicked(View view) {
        if (contactManager.getContactCount() == 0) {
            Toast.makeText(this, "No emergency contacts configured", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isServiceBound && fallDetectionService != null) {
            fallDetectionService.getEmergencyManager().testEmergencyProcedures();
            Toast.makeText(this, "Test emergency alert sent", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Service not available", Toast.LENGTH_SHORT).show();
        }
    }

    // FallDetectionService.ServiceListener implementation
    @Override
    public void onServiceStateChanged(boolean isRunning, boolean isMonitoring) {
        Log.d(TAG, "Service state changed - Running: " + isRunning + ", Monitoring: " + isMonitoring);
        updateUI();
    }

    @Override
    public void onFallDetected(float confidence) {
        Log.w(TAG, "Fall detected in UI - Confidence: " + confidence);

        runOnUiThread(() -> {
            Toast.makeText(this, "FALL DETECTED! Confidence: " + String.format("%.1f%%", confidence * 100),
                Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void onEmergencyStateChanged(boolean isActive, int countdown) {
        Log.d(TAG, "Emergency state changed - Active: " + isActive + ", Countdown: " + countdown);

        runOnUiThread(() -> {
            if (isActive && countdown > 0) {
                tvEmergencyStatus.setText("Emergency: Countdown " + countdown + "s");
            } else if (isActive) {
                tvEmergencyStatus.setText("Emergency: ACTIVE");
            } else {
                tvEmergencyStatus.setText("Emergency: None");
            }
        });
    }
}