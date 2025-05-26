package com.tejalabs.falldetection.activities;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.tejalabs.falldetection.R;
import com.tejalabs.falldetection.utils.SharedPreferencesManager;

public class SettingsActivity extends AppCompatActivity {

    private static final String TAG = "SettingsActivity";

    // UI Components
    private Switch switchAutoStart;
    private Switch switchEmergencyCall;
    private Switch switchEmergencySMS;
    private Switch switchLocationTracking;
    private SeekBar seekBarSensitivity;
    private SeekBar seekBarCountdown;
    private TextView tvSensitivityValue;
    private TextView tvCountdownValue;

    // Utilities
    private SharedPreferencesManager prefsManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Log.d(TAG, "SettingsActivity created");

        // Initialize utilities
        prefsManager = SharedPreferencesManager.getInstance(this);

        // Setup toolbar
        setupToolbar();

        // Initialize UI
        initializeUI();

        // Load current settings
        loadSettings();
    }

    /**
     * Setup toolbar with back button
     */
    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setTitle("Settings");
        }
    }

    /**
     * Initialize UI components
     */
    private void initializeUI() {
        // Find views
        switchAutoStart = findViewById(R.id.switch_auto_start);
        switchEmergencyCall = findViewById(R.id.switch_emergency_call);
        switchEmergencySMS = findViewById(R.id.switch_emergency_sms);
        switchLocationTracking = findViewById(R.id.switch_location_tracking);
        seekBarSensitivity = findViewById(R.id.seekbar_sensitivity);
        seekBarCountdown = findViewById(R.id.seekbar_countdown);
        tvSensitivityValue = findViewById(R.id.tv_sensitivity_value);
        tvCountdownValue = findViewById(R.id.tv_countdown_value);

        // Set up listeners
        setupListeners();
    }

    /**
     * Setup event listeners
     */
    private void setupListeners() {
        // Switch listeners
        switchAutoStart.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefsManager.setAutoStartEnabled(isChecked);
            Log.d(TAG, "Auto start: " + isChecked);
        });

        switchEmergencyCall.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefsManager.setEmergencyCallEnabled(isChecked);
            Log.d(TAG, "Emergency call: " + isChecked);
        });

        switchEmergencySMS.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefsManager.setEmergencySMSEnabled(isChecked);
            Log.d(TAG, "Emergency SMS: " + isChecked);
        });

        switchLocationTracking.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefsManager.setLocationTrackingEnabled(isChecked);
            Log.d(TAG, "Location tracking: " + isChecked);
        });

        // Sensitivity seekbar
        seekBarSensitivity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    float sensitivity = 0.5f + (progress / 100.0f) * 1.0f; // Range: 0.5 to 1.5
                    tvSensitivityValue.setText(String.format("%.1f", sensitivity));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                float sensitivity = 0.5f + (seekBar.getProgress() / 100.0f) * 1.0f;
                prefsManager.setSensitivityThreshold(sensitivity);
                Toast.makeText(SettingsActivity.this, "Sensitivity updated", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Sensitivity: " + sensitivity);
            }
        });

        // Countdown seekbar
        seekBarCountdown.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    int countdown = 5 + progress; // Range: 5 to 35 seconds
                    tvCountdownValue.setText(countdown + " seconds");
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int countdown = 5 + seekBar.getProgress();
                prefsManager.setEmergencyCountdown(countdown);
                Toast.makeText(SettingsActivity.this, "Countdown updated", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Emergency countdown: " + countdown);
            }
        });
    }

    /**
     * Load current settings from preferences
     */
    private void loadSettings() {
        try {
            // Load switch states
            switchAutoStart.setChecked(prefsManager.isAutoStartEnabled());
            switchEmergencyCall.setChecked(prefsManager.isEmergencyCallEnabled());
            switchEmergencySMS.setChecked(prefsManager.isEmergencySMSEnabled());
            switchLocationTracking.setChecked(prefsManager.isLocationTrackingEnabled());

            // Load sensitivity
            float sensitivity = prefsManager.getSensitivityThreshold();
            int sensitivityProgress = (int) ((sensitivity - 0.5f) / 1.0f * 100);
            seekBarSensitivity.setProgress(Math.max(0, Math.min(100, sensitivityProgress)));
            tvSensitivityValue.setText(String.format("%.1f", sensitivity));

            // Load countdown
            int countdown = prefsManager.getEmergencyCountdown();
            seekBarCountdown.setProgress(Math.max(0, Math.min(30, countdown - 5)));
            tvCountdownValue.setText(countdown + " seconds");

            Log.d(TAG, "Settings loaded successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error loading settings", e);
            Toast.makeText(this, "Error loading settings", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
