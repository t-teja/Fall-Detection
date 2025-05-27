package com.tejalabs.falldetection.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.tejalabs.falldetection.R;
import com.tejalabs.falldetection.services.FallDetectionService;

/**
 * Activity shown when user clicks on fall detection notification
 * Provides a clear interface to confirm or cancel the emergency alert
 */
public class EmergencyConfirmationActivity extends AppCompatActivity {

    private static final String TAG = "EmergencyConfirmation";

    private TextView tvTitle;
    private TextView tvMessage;
    private TextView tvCountdown;
    private Button btnCancel;
    private Button btnConfirmEmergency;

    private CountDownTimer countdownTimer;
    private int remainingSeconds = 30; // Default countdown

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emergency_confirmation);

        // Make this activity appear over lock screen and other apps
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            );
        }

        initializeViews();
        setupClickListeners();
        startCountdownDisplay();

        Log.d(TAG, "Emergency confirmation activity created");
    }

    private void initializeViews() {
        tvTitle = findViewById(R.id.tv_emergency_title);
        tvMessage = findViewById(R.id.tv_emergency_message);
        tvCountdown = findViewById(R.id.tv_emergency_countdown);
        btnCancel = findViewById(R.id.btn_cancel_emergency);
        btnConfirmEmergency = findViewById(R.id.btn_confirm_emergency);

        // Set initial text
        tvTitle.setText("FALL DETECTED!");
        tvMessage.setText("Emergency procedures will activate automatically unless cancelled.\n\nAre you okay?");
    }

    private void setupClickListeners() {
        btnCancel.setOnClickListener(v -> cancelEmergency());
        btnConfirmEmergency.setOnClickListener(v -> confirmEmergency());

        // Also allow back button to cancel
        findViewById(android.R.id.content).setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK) {
                cancelEmergency();
                return true;
            }
            return false;
        });
    }

    private void startCountdownDisplay() {
        // Get remaining time from intent if available
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("remaining_seconds")) {
            remainingSeconds = intent.getIntExtra("remaining_seconds", 30);
        }

        countdownTimer = new CountDownTimer(remainingSeconds * 1000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int seconds = (int) (millisUntilFinished / 1000);
                updateCountdownDisplay(seconds);
            }

            @Override
            public void onFinish() {
                // Time's up - emergency will activate automatically
                tvCountdown.setText("Emergency procedures activating...");
                btnCancel.setEnabled(false);
                btnConfirmEmergency.setEnabled(false);

                // Close activity after a short delay
                tvCountdown.postDelayed(() -> finish(), 2000);
            }
        };

        countdownTimer.start();
        updateCountdownDisplay(remainingSeconds);
    }

    private void updateCountdownDisplay(int seconds) {
        tvCountdown.setText(String.format("Time remaining: %d seconds", seconds));

        // Change color as time runs out
        if (seconds <= 10) {
            tvCountdown.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
        } else if (seconds <= 20) {
            tvCountdown.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
        }
    }

    private void cancelEmergency() {
        Log.i(TAG, "User cancelled emergency");

        // Stop countdown
        if (countdownTimer != null) {
            countdownTimer.cancel();
        }

        try {
            // Send cancel broadcast using service constant
            Intent cancelIntent = new Intent(FallDetectionService.ACTION_CANCEL_EMERGENCY);
            sendBroadcast(cancelIntent);
            Log.d(TAG, "Cancel broadcast sent: " + FallDetectionService.ACTION_CANCEL_EMERGENCY);

            // Also try starting the service directly with the cancel action
            Intent serviceIntent = new Intent(this, FallDetectionService.class);
            serviceIntent.setAction(FallDetectionService.ACTION_CANCEL_EMERGENCY);
            startService(serviceIntent);
            Log.d(TAG, "Cancel service intent sent");

        } catch (Exception e) {
            Log.e(TAG, "Error sending cancel intent", e);
        }

        // Show confirmation
        tvTitle.setText("Emergency Cancelled");
        tvMessage.setText("Fall detection alert has been cancelled.\nMonitoring will continue.");
        tvCountdown.setText("You can close this screen now.");

        btnCancel.setEnabled(false);
        btnConfirmEmergency.setEnabled(false);

        // Auto-close after showing confirmation
        tvTitle.postDelayed(() -> finish(), 3000);
    }

    private void confirmEmergency() {
        Log.i(TAG, "User confirmed emergency");

        // Stop countdown
        if (countdownTimer != null) {
            countdownTimer.cancel();
        }

        try {
            // Send emergency activation broadcast using service constant
            Intent emergencyIntent = new Intent(FallDetectionService.ACTION_ACTIVATE_EMERGENCY);
            sendBroadcast(emergencyIntent);
            Log.d(TAG, "Activate broadcast sent: " + FallDetectionService.ACTION_ACTIVATE_EMERGENCY);

            // Also try starting the service directly with the activate action
            Intent serviceIntent = new Intent(this, FallDetectionService.class);
            serviceIntent.setAction(FallDetectionService.ACTION_ACTIVATE_EMERGENCY);
            startService(serviceIntent);
            Log.d(TAG, "Activate service intent sent");

        } catch (Exception e) {
            Log.e(TAG, "Error sending activate intent", e);
        }

        // Show confirmation
        tvTitle.setText("Emergency Activated");
        tvMessage.setText("Emergency procedures are being activated immediately.\nHelp is on the way!");
        tvCountdown.setText("Emergency contacts are being notified...");

        btnCancel.setEnabled(false);
        btnConfirmEmergency.setEnabled(false);

        // Auto-close after showing confirmation
        tvTitle.postDelayed(() -> finish(), 3000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countdownTimer != null) {
            countdownTimer.cancel();
        }
        Log.d(TAG, "Emergency confirmation activity destroyed");
    }

    @Override
    public void onBackPressed() {
        // Treat back button as cancel
        cancelEmergency();
    }
}
