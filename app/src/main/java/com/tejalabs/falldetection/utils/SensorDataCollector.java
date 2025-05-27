package com.tejalabs.falldetection.utils;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Collects and processes sensor data for fall detection
 * Handles accelerometer, gyroscope, and magnetometer data
 */
public class SensorDataCollector implements SensorEventListener {

    private static final String TAG = "SensorDataCollector";

    // Sensor sampling rate (microseconds)
    private static final int SENSOR_DELAY = SensorManager.SENSOR_DELAY_GAME; // ~50Hz

    // Data window size for analysis (number of samples)
    private static final int WINDOW_SIZE = 50; // 1 second at 50Hz
    private static final int OVERLAP_SIZE = 25; // 50% overlap

    // Sensor data storage
    private List<Float[]> accelerometerData;
    private List<Float[]> gyroscopeData;
    private List<Float[]> magnetometerData;
    private List<Long> timestamps;

    // Sensor managers and sensors
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor gyroscope;
    private Sensor magnetometer;

    // Data processing
    private boolean isCollecting = false;
    private SensorDataListener dataListener;
    private long lastProcessingTime = 0;
    private static final long PROCESSING_INTERVAL = 100; // Process every 100ms

    // Gravity and linear acceleration
    private float[] gravity = new float[3];
    private float[] linearAcceleration = new float[3];
    private static final float ALPHA = 0.8f; // Low-pass filter constant

    public interface SensorDataListener {
        void onDataProcessed(SensorDataWindow dataWindow);
        void onFallDetected(float confidence);
    }

    public static class SensorDataWindow {
        public List<Float[]> accelerometerData;
        public List<Float[]> gyroscopeData;
        public List<Float[]> magnetometerData;
        public List<Long> timestamps;
        public float[] features;

        public SensorDataWindow() {
            accelerometerData = new ArrayList<>();
            gyroscopeData = new ArrayList<>();
            magnetometerData = new ArrayList<>();
            timestamps = new ArrayList<>();
        }
    }

    public SensorDataCollector(Context context) {
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);

        // Initialize sensors
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        // Initialize data storage
        accelerometerData = new CopyOnWriteArrayList<>();
        gyroscopeData = new CopyOnWriteArrayList<>();
        magnetometerData = new CopyOnWriteArrayList<>();
        timestamps = new CopyOnWriteArrayList<>();

        Log.d(TAG, "SensorDataCollector initialized");
    }

    public void setDataListener(SensorDataListener listener) {
        this.dataListener = listener;
    }

    public boolean startCollection() {
        if (isCollecting) {
            Log.w(TAG, "Sensor collection already started");
            return true;
        }

        boolean success = true;

        // Register accelerometer
        if (accelerometer != null) {
            success &= sensorManager.registerListener(this, accelerometer, SENSOR_DELAY);
        } else {
            Log.e(TAG, "Accelerometer not available");
            return false;
        }

        // Register gyroscope (optional)
        if (gyroscope != null) {
            success &= sensorManager.registerListener(this, gyroscope, SENSOR_DELAY);
        } else {
            Log.w(TAG, "Gyroscope not available");
        }

        // Register magnetometer (optional)
        if (magnetometer != null) {
            success &= sensorManager.registerListener(this, magnetometer, SENSOR_DELAY);
        } else {
            Log.w(TAG, "Magnetometer not available");
        }

        if (success) {
            isCollecting = true;
            Log.d(TAG, "Sensor collection started");
        } else {
            Log.e(TAG, "Failed to start sensor collection");
        }

        return success;
    }

    public void stopCollection() {
        if (!isCollecting) {
            return;
        }

        sensorManager.unregisterListener(this);
        isCollecting = false;
        clearData();
        Log.d(TAG, "Sensor collection stopped");
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!isCollecting) return;

        long currentTime = System.currentTimeMillis();
        Float[] values = new Float[]{event.values[0], event.values[1], event.values[2]};

        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                // Apply low-pass filter to isolate gravity
                gravity[0] = ALPHA * gravity[0] + (1 - ALPHA) * event.values[0];
                gravity[1] = ALPHA * gravity[1] + (1 - ALPHA) * event.values[1];
                gravity[2] = ALPHA * gravity[2] + (1 - ALPHA) * event.values[2];

                // Calculate linear acceleration (remove gravity)
                linearAcceleration[0] = event.values[0] - gravity[0];
                linearAcceleration[1] = event.values[1] - gravity[1];
                linearAcceleration[2] = event.values[2] - gravity[2];

                accelerometerData.add(values);
                timestamps.add(currentTime);
                break;

            case Sensor.TYPE_GYROSCOPE:
                gyroscopeData.add(values);
                break;

            case Sensor.TYPE_MAGNETIC_FIELD:
                magnetometerData.add(values);
                break;
        }

        // Process data periodically
        if (currentTime - lastProcessingTime > PROCESSING_INTERVAL) {
            processDataWindow();
            lastProcessingTime = currentTime;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.d(TAG, "Sensor accuracy changed: " + sensor.getName() + " accuracy: " + accuracy);
    }

    private void processDataWindow() {
        if (accelerometerData.size() < WINDOW_SIZE) {
            return; // Not enough data yet
        }

        // Create data window
        SensorDataWindow window = new SensorDataWindow();

        // Copy data for processing (last WINDOW_SIZE samples)
        int startIndex = Math.max(0, accelerometerData.size() - WINDOW_SIZE);

        for (int i = startIndex; i < accelerometerData.size(); i++) {
            window.accelerometerData.add(accelerometerData.get(i));
            window.timestamps.add(timestamps.get(i));

            // Add gyroscope data if available
            if (i < gyroscopeData.size()) {
                window.gyroscopeData.add(gyroscopeData.get(i));
            }

            // Add magnetometer data if available
            if (i < magnetometerData.size()) {
                window.magnetometerData.add(magnetometerData.get(i));
            }
        }

        // Extract features
        window.features = extractFeatures(window);

        // Notify listener
        if (dataListener != null) {
            dataListener.onDataProcessed(window);
        }

        // Check for fall pattern
        checkForFall(window);

        // Remove old data (keep overlap)
        removeOldData();
    }

    private float[] extractFeatures(SensorDataWindow window) {
        List<Float> features = new ArrayList<>();

        if (!window.accelerometerData.isEmpty()) {
            // Calculate magnitude of acceleration
            List<Float> magnitudes = new ArrayList<>();
            for (Float[] acc : window.accelerometerData) {
                float magnitude = (float) Math.sqrt(acc[0]*acc[0] + acc[1]*acc[1] + acc[2]*acc[2]);
                magnitudes.add(magnitude);
            }

            // Statistical features
            features.add(calculateMean(magnitudes));
            features.add(calculateStandardDeviation(magnitudes));
            features.add(calculateMax(magnitudes));
            features.add(calculateMin(magnitudes));
            features.add(calculateRange(magnitudes));

            // Add individual axis statistics
            for (int axis = 0; axis < 3; axis++) {
                List<Float> axisData = new ArrayList<>();
                for (Float[] acc : window.accelerometerData) {
                    axisData.add(acc[axis]);
                }
                features.add(calculateMean(axisData));
                features.add(calculateStandardDeviation(axisData));
            }
        }

        // Convert to array
        float[] featureArray = new float[features.size()];
        for (int i = 0; i < features.size(); i++) {
            featureArray[i] = features.get(i);
        }

        return featureArray;
    }

    private void checkForFall(SensorDataWindow window) {
        // Fall detection is now handled by TinyMLProcessor
        // This method is kept for compatibility but does nothing
        // The actual fall detection happens in FallDetectionService via TinyMLProcessor
    }

    private void removeOldData() {
        int removeCount = WINDOW_SIZE - OVERLAP_SIZE;

        if (accelerometerData.size() > removeCount) {
            for (int i = 0; i < removeCount; i++) {
                accelerometerData.remove(0);
                timestamps.remove(0);

                if (!gyroscopeData.isEmpty()) {
                    gyroscopeData.remove(0);
                }

                if (!magnetometerData.isEmpty()) {
                    magnetometerData.remove(0);
                }
            }
        }
    }

    private void clearData() {
        accelerometerData.clear();
        gyroscopeData.clear();
        magnetometerData.clear();
        timestamps.clear();
    }

    // Statistical helper methods
    private float calculateMean(List<Float> data) {
        float sum = 0;
        for (float value : data) {
            sum += value;
        }
        return sum / data.size();
    }

    private float calculateStandardDeviation(List<Float> data) {
        float mean = calculateMean(data);
        float sum = 0;
        for (float value : data) {
            sum += Math.pow(value - mean, 2);
        }
        return (float) Math.sqrt(sum / data.size());
    }

    private float calculateMax(List<Float> data) {
        float max = Float.MIN_VALUE;
        for (float value : data) {
            if (value > max) max = value;
        }
        return max;
    }

    private float calculateMin(List<Float> data) {
        float min = Float.MAX_VALUE;
        for (float value : data) {
            if (value < min) min = value;
        }
        return min;
    }

    private float calculateRange(List<Float> data) {
        return calculateMax(data) - calculateMin(data);
    }

    public boolean isCollecting() {
        return isCollecting;
    }
}
