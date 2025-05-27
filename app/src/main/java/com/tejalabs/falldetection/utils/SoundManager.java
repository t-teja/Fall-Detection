package com.tejalabs.falldetection.utils;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * Manages sound playback for emergency alerts
 * Handles siren sounds and other audio notifications
 */
public class SoundManager {

    private static final String TAG = "SoundManager";
    private static SoundManager instance;

    private Context context;
    private ToneGenerator toneGenerator;
    private MediaPlayer mediaPlayer;
    private Handler soundHandler;
    private boolean isPlayingSiren = false;

    // Siren pattern configuration
    private static final int SIREN_DURATION_MS = 10000; // 10 seconds
    private static final int TONE_DURATION_MS = 500; // Each tone duration
    private static final int TONE_PAUSE_MS = 200; // Pause between tones

    private SoundManager(Context context) {
        this.context = context.getApplicationContext();
        this.soundHandler = new Handler(Looper.getMainLooper());
        initializeToneGenerator();
    }

    public static synchronized SoundManager getInstance(Context context) {
        if (instance == null) {
            instance = new SoundManager(context);
        }
        return instance;
    }

    /**
     * Initialize tone generator for siren sounds
     */
    private void initializeToneGenerator() {
        try {
            // Create tone generator with alarm stream
            toneGenerator = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
            Log.d(TAG, "ToneGenerator initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize ToneGenerator", e);
            toneGenerator = null;
        }
    }

    /**
     * Play emergency siren sound
     */
    public void playEmergencySiren() {
        if (isPlayingSiren) {
            Log.w(TAG, "Siren already playing");
            return;
        }

        Log.i(TAG, "Starting emergency siren");
        isPlayingSiren = true;

        // Try to use ToneGenerator first, fallback to MediaPlayer
        if (toneGenerator != null) {
            playToneSiren();
        } else {
            playMediaPlayerSiren();
        }

        // Auto-stop after duration
        soundHandler.postDelayed(this::stopSiren, SIREN_DURATION_MS);
    }

    /**
     * Play siren using ToneGenerator (alternating high/low tones)
     */
    private void playToneSiren() {
        final Runnable sirenRunnable = new Runnable() {
            private boolean highTone = true;

            @Override
            public void run() {
                if (!isPlayingSiren || toneGenerator == null) {
                    return;
                }

                try {
                    // Alternate between high and low frequency tones
                    int toneType = highTone ? ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK :
                                             ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD;

                    toneGenerator.startTone(toneType, TONE_DURATION_MS);
                    highTone = !highTone;

                    // Schedule next tone
                    soundHandler.postDelayed(this, TONE_DURATION_MS + TONE_PAUSE_MS);

                } catch (Exception e) {
                    Log.e(TAG, "Error playing tone", e);
                    stopSiren();
                }
            }
        };

        soundHandler.post(sirenRunnable);
    }

    /**
     * Play siren using MediaPlayer (fallback method)
     */
    private void playMediaPlayerSiren() {
        try {
            // Create a simple beeping pattern using system sounds
            final Runnable beepRunnable = new Runnable() {
                private int beepCount = 0;
                private final int maxBeeps = SIREN_DURATION_MS / (TONE_DURATION_MS + TONE_PAUSE_MS);

                @Override
                public void run() {
                    if (!isPlayingSiren || beepCount >= maxBeeps) {
                        stopSiren();
                        return;
                    }

                    try {
                        // Play system notification sound as fallback
                        if (mediaPlayer != null) {
                            mediaPlayer.release();
                        }

                        // Use system default notification sound
                        try {
                            mediaPlayer = MediaPlayer.create(context, android.provider.Settings.System.DEFAULT_NOTIFICATION_URI);
                            if (mediaPlayer != null) {
                                mediaPlayer.setAudioAttributes(
                                    new AudioAttributes.Builder()
                                        .setUsage(AudioAttributes.USAGE_ALARM)
                                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                        .build()
                                );
                                mediaPlayer.start();
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to create MediaPlayer with system sound", e);
                        }

                        beepCount++;
                        soundHandler.postDelayed(this, TONE_DURATION_MS + TONE_PAUSE_MS);

                    } catch (Exception e) {
                        Log.e(TAG, "Error playing media sound", e);
                        stopSiren();
                    }
                }
            };

            soundHandler.post(beepRunnable);

        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize MediaPlayer siren", e);
            stopSiren();
        }
    }

    /**
     * Stop the siren sound
     */
    public void stopSiren() {
        if (!isPlayingSiren) {
            return;
        }

        Log.i(TAG, "Stopping emergency siren");
        isPlayingSiren = false;

        // Remove any pending sound callbacks
        soundHandler.removeCallbacksAndMessages(null);

        // Stop tone generator
        if (toneGenerator != null) {
            try {
                toneGenerator.stopTone();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping tone generator", e);
            }
        }

        // Stop media player
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
                mediaPlayer = null;
            } catch (Exception e) {
                Log.e(TAG, "Error stopping media player", e);
            }
        }
    }

    /**
     * Play a short alert beep
     */
    public void playAlertBeep() {
        if (toneGenerator != null) {
            try {
                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 200);
            } catch (Exception e) {
                Log.e(TAG, "Error playing alert beep", e);
            }
        }
    }

    /**
     * Check if siren is currently playing
     */
    public boolean isPlayingSiren() {
        return isPlayingSiren;
    }

    /**
     * Clean up resources
     */
    public void cleanup() {
        stopSiren();

        if (toneGenerator != null) {
            try {
                toneGenerator.release();
                toneGenerator = null;
            } catch (Exception e) {
                Log.e(TAG, "Error releasing tone generator", e);
            }
        }

        if (mediaPlayer != null) {
            try {
                mediaPlayer.release();
                mediaPlayer = null;
            } catch (Exception e) {
                Log.e(TAG, "Error releasing media player", e);
            }
        }

        Log.d(TAG, "SoundManager cleaned up");
    }
}
