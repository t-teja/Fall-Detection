package com.tejalabs.falldetection.utils;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.util.Log;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TensorFlow Lite processor for fall detection using TinyML
 * Handles model loading, inference, and result interpretation
 */
public class TinyMLProcessor {

    private static final String TAG = "TinyMLProcessor";

    // Model configuration
    private static final String MODEL_FILENAME = "fall_detection_model.tflite";
    private static final int INPUT_SIZE = 150; // 50 samples * 3 axes
    private static final int OUTPUT_SIZE = 2; // [no_fall, fall] probabilities

    // TensorFlow Lite components
    private Interpreter tflite;
    private ByteBuffer inputBuffer;
    private float[][] outputBuffer;

    // Model metadata
    private boolean isModelLoaded = false;
    private int inputTensorIndex = 0;
    private int outputTensorIndex = 0;

    // Processing parameters
    private float fallThreshold = 0.7f; // Confidence threshold for fall detection
    private SharedPreferencesManager prefsManager;
    private AdaptiveLearningEngine learningEngine;

    public TinyMLProcessor(Context context) {
        prefsManager = SharedPreferencesManager.getInstance(context);
        learningEngine = new AdaptiveLearningEngine(context);
        initializeModel(context);
    }

    /**
     * Initialize the TensorFlow Lite model
     */
    private void initializeModel(Context context) {
        try {
            // Load model from assets
            MappedByteBuffer modelBuffer = loadModelFile(context);

            // Create interpreter options
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(2); // Use 2 threads for better performance
            options.setUseNNAPI(true); // Use Android Neural Networks API if available

            // Create interpreter
            tflite = new Interpreter(modelBuffer, options);

            // Initialize input/output buffers
            initializeBuffers();

            isModelLoaded = true;
            Log.d(TAG, "TensorFlow Lite model loaded successfully");

        } catch (IOException e) {
            Log.e(TAG, "Error loading TensorFlow Lite model", e);
            // Create a fallback simple model
            createFallbackModel();
        }
    }

    /**
     * Load model file from assets
     */
    private MappedByteBuffer loadModelFile(Context context) throws IOException {
        try {
            // Try to load from assets
            AssetFileDescriptor fileDescriptor = context.getAssets().openFd(MODEL_FILENAME);
            FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
            FileChannel fileChannel = inputStream.getChannel();
            long startOffset = fileDescriptor.getStartOffset();
            long declaredLength = fileDescriptor.getDeclaredLength();
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
        } catch (IOException e) {
            Log.w(TAG, "Model file not found in assets, using fallback");
            throw e;
        }
    }

    /**
     * Initialize input and output buffers
     */
    private void initializeBuffers() {
        // Input buffer: float32 values for sensor data
        inputBuffer = ByteBuffer.allocateDirect(INPUT_SIZE * 4); // 4 bytes per float
        inputBuffer.order(ByteOrder.nativeOrder());

        // Output buffer: probabilities for [no_fall, fall]
        outputBuffer = new float[1][OUTPUT_SIZE];

        Log.d(TAG, "Buffers initialized - Input size: " + INPUT_SIZE + ", Output size: " + OUTPUT_SIZE);
    }

    /**
     * Create a simple fallback model when TFLite model is not available
     */
    private void createFallbackModel() {
        Log.w(TAG, "Using fallback rule-based fall detection");
        isModelLoaded = false; // Will use rule-based detection
    }

    /**
     * Process sensor data and detect falls
     */
    public FallDetectionResult processSensorData(SensorDataCollector.SensorDataWindow dataWindow) {
        if (dataWindow == null || dataWindow.accelerometerData.isEmpty()) {
            return new FallDetectionResult(false, 0.0f, "No data available");
        }

        if (isModelLoaded && tflite != null) {
            return processWithTFLite(dataWindow);
        } else {
            return processWithRuleBasedDetection(dataWindow);
        }
    }

    /**
     * Process data using TensorFlow Lite model
     */
    private FallDetectionResult processWithTFLite(SensorDataCollector.SensorDataWindow dataWindow) {
        try {
            // Prepare input data
            prepareInputData(dataWindow);

            // Run inference
            Map<Integer, Object> outputs = new HashMap<>();
            outputs.put(outputTensorIndex, outputBuffer);

            tflite.runForMultipleInputsOutputs(new Object[]{inputBuffer}, outputs);

            // Interpret results
            float fallProbability = outputBuffer[0][1]; // Probability of fall
            boolean isFall = fallProbability > fallThreshold;

            String details = String.format("TFLite inference - Fall probability: %.3f", fallProbability);

            return new FallDetectionResult(isFall, fallProbability, details);

        } catch (Exception e) {
            Log.e(TAG, "Error during TFLite inference", e);
            // Fallback to rule-based detection
            return processWithRuleBasedDetection(dataWindow);
        }
    }

    /**
     * Prepare input data for TensorFlow Lite model
     */
    private void prepareInputData(SensorDataCollector.SensorDataWindow dataWindow) {
        inputBuffer.rewind();

        // Normalize and flatten accelerometer data
        int sampleCount = Math.min(50, dataWindow.accelerometerData.size()); // Use last 50 samples
        int startIndex = Math.max(0, dataWindow.accelerometerData.size() - sampleCount);

        for (int i = startIndex; i < dataWindow.accelerometerData.size(); i++) {
            Float[] sample = dataWindow.accelerometerData.get(i);

            // Normalize values (assuming typical range -20 to +20 m/s²)
            for (int axis = 0; axis < 3; axis++) {
                float normalizedValue = Math.max(-1.0f, Math.min(1.0f, sample[axis] / 20.0f));
                inputBuffer.putFloat(normalizedValue);
            }
        }

        // Pad with zeros if we have fewer than 50 samples
        while (inputBuffer.position() < INPUT_SIZE * 4) {
            inputBuffer.putFloat(0.0f);
        }
    }

    /**
     * Process data using advanced rule-based fall detection with TinyML-like features
     */
    private FallDetectionResult processWithRuleBasedDetection(SensorDataCollector.SensorDataWindow dataWindow) {
        if (dataWindow.accelerometerData.isEmpty()) {
            return new FallDetectionResult(false, 0.0f, "No accelerometer data");
        }

        // Extract comprehensive features
        MotionFeatures features = extractMotionFeatures(dataWindow);

        // Get sensitivity multiplier (inverted - lower setting = higher threshold)
        float sensitivityMultiplier = getSensitivityMultiplier();

        // Advanced fall detection algorithm
        FallDetectionResult result = detectFallWithAdvancedAlgorithm(features, sensitivityMultiplier);

        return result;
    }

    /**
     * Extract comprehensive motion features from sensor data
     */
    private MotionFeatures extractMotionFeatures(SensorDataCollector.SensorDataWindow dataWindow) {
        List<Float> magnitudes = new ArrayList<>();
        List<Float> verticalAccel = new ArrayList<>();
        List<Float> horizontalMagnitudes = new ArrayList<>();

        float totalMagnitude = 0.0f;
        float maxMagnitude = 0.0f;
        float minMagnitude = Float.MAX_VALUE;

        // Calculate magnitudes and extract features
        for (Float[] sample : dataWindow.accelerometerData) {
            float magnitude = (float) Math.sqrt(
                sample[0] * sample[0] +
                sample[1] * sample[1] +
                sample[2] * sample[2]
            );

            float horizontalMag = (float) Math.sqrt(sample[0] * sample[0] + sample[1] * sample[1]);

            magnitudes.add(magnitude);
            verticalAccel.add(Math.abs(sample[2])); // Z-axis (vertical when phone is upright)
            horizontalMagnitudes.add(horizontalMag);

            maxMagnitude = Math.max(maxMagnitude, magnitude);
            minMagnitude = Math.min(minMagnitude, magnitude);
            totalMagnitude += magnitude;
        }

        float avgMagnitude = totalMagnitude / magnitudes.size();

        // Calculate statistical features
        float variance = calculateVariance(magnitudes, avgMagnitude);
        float standardDeviation = (float) Math.sqrt(variance);

        // Calculate jerk (rate of change of acceleration)
        float maxJerk = calculateMaxJerk(magnitudes);

        // Calculate orientation change
        float orientationChange = calculateOrientationChange(dataWindow);

        // Calculate frequency domain features
        float dominantFrequency = calculateDominantFrequency(magnitudes);

        return new MotionFeatures(
            maxMagnitude, minMagnitude, avgMagnitude, standardDeviation,
            maxJerk, orientationChange, dominantFrequency,
            calculateMean(verticalAccel), calculateMean(horizontalMagnitudes)
        );
    }

    /**
     * Advanced fall detection algorithm using multiple features
     */
    private FallDetectionResult detectFallWithAdvancedAlgorithm(MotionFeatures features, float sensitivityMultiplier) {
        // Base thresholds (these will be scaled by sensitivity)
        float impactThreshold = 25.0f * sensitivityMultiplier;      // High acceleration threshold
        float freeFallThreshold = 4.0f / sensitivityMultiplier;    // Low acceleration threshold
        float jerkThreshold = 15.0f * sensitivityMultiplier;       // Sudden change threshold
        float orientationThreshold = 45.0f / sensitivityMultiplier; // Orientation change threshold
        float variationThreshold = 12.0f * sensitivityMultiplier;  // Acceleration variation threshold

        // Feature-based detection
        boolean hasHighImpact = features.maxMagnitude > impactThreshold;
        boolean hasFreeFall = features.minMagnitude < freeFallThreshold;
        boolean hasHighJerk = features.maxJerk > jerkThreshold;
        boolean hasOrientationChange = features.orientationChange > orientationThreshold;
        boolean hasHighVariation = (features.maxMagnitude - features.minMagnitude) > variationThreshold;
        boolean hasAbnormalStdDev = features.standardDeviation > (8.0f * sensitivityMultiplier);

        // Additional checks to reduce false positives
        boolean isNotJustVibration = features.dominantFrequency < 15.0f; // Not high-frequency vibration
        boolean hasReasonableDuration = true; // We assume 2-second window is reasonable
        boolean isNotJustPlacement = features.maxMagnitude < (50.0f * sensitivityMultiplier); // Not just phone placement

        // Calculate confidence score using weighted features
        float confidence = 0.0f;

        if (hasHighImpact) confidence += 0.25f;
        if (hasFreeFall) confidence += 0.20f;
        if (hasHighJerk) confidence += 0.20f;
        if (hasOrientationChange) confidence += 0.15f;
        if (hasHighVariation) confidence += 0.10f;
        if (hasAbnormalStdDev) confidence += 0.10f;

        // Apply penalties for likely false positives
        if (!isNotJustVibration) confidence *= 0.3f;
        if (!isNotJustPlacement) confidence *= 0.5f;

        // Normalize confidence
        confidence = Math.min(1.0f, confidence);

        // Apply adaptive learning adjustment
        float originalConfidence = confidence;
        confidence = learningEngine.getConfidenceAdjustment(features, confidence);

        // Check if similar to known false alarms
        boolean isSimilarToFalseAlarm = learningEngine.isSimilarToFalseAlarm(features, confidence);

        // Final decision: require multiple indicators for fall detection
        boolean isFall = confidence > 0.7f &&
                        hasHighImpact &&
                        (hasFreeFall || hasHighJerk) &&
                        isNotJustVibration &&
                        isNotJustPlacement &&
                        !isSimilarToFalseAlarm; // Don't trigger if similar to learned false alarms

        String details = String.format(
            "TinyML - Impact: %.1f, FreeFall: %.1f, Jerk: %.1f, Orient: %.1f°, StdDev: %.2f, Conf: %.3f%s%s",
            features.maxMagnitude, features.minMagnitude, features.maxJerk,
            features.orientationChange, features.standardDeviation, confidence,
            (originalConfidence != confidence) ? String.format(" (adj from %.3f)", originalConfidence) : "",
            isSimilarToFalseAlarm ? " [Similar to false alarm]" : ""
        );

        return new FallDetectionResult(isFall, confidence, details);
    }

    /**
     * Get sensitivity multiplier based on user settings
     */
    private float getSensitivityMultiplier() {
        int sensitivityLevel = prefsManager.getSensitivityLevel();
        switch (sensitivityLevel) {
            case 1: return 3.0f;  // Very Low sensitivity (high thresholds)
            case 2: return 2.0f;  // Low sensitivity
            case 3: return 1.0f;  // Medium sensitivity (default)
            case 4: return 0.7f;  // High sensitivity
            case 5: return 0.5f;  // Very High sensitivity (low thresholds)
            default: return 1.0f;
        }
    }

    /**
     * Calculate variance of a list of values
     */
    private float calculateVariance(List<Float> values, float mean) {
        float sum = 0.0f;
        for (float value : values) {
            float diff = value - mean;
            sum += diff * diff;
        }
        return sum / values.size();
    }

    /**
     * Calculate maximum jerk (rate of change of acceleration)
     */
    private float calculateMaxJerk(List<Float> magnitudes) {
        if (magnitudes.size() < 2) return 0.0f;

        float maxJerk = 0.0f;
        for (int i = 1; i < magnitudes.size(); i++) {
            float jerk = Math.abs(magnitudes.get(i) - magnitudes.get(i - 1));
            maxJerk = Math.max(maxJerk, jerk);
        }
        return maxJerk;
    }

    /**
     * Calculate orientation change using accelerometer data
     */
    private float calculateOrientationChange(SensorDataCollector.SensorDataWindow dataWindow) {
        if (dataWindow.accelerometerData.size() < 2) return 0.0f;

        Float[] first = dataWindow.accelerometerData.get(0);
        Float[] last = dataWindow.accelerometerData.get(dataWindow.accelerometerData.size() - 1);

        // Calculate angle between first and last acceleration vectors
        float dot = first[0] * last[0] + first[1] * last[1] + first[2] * last[2];
        float mag1 = (float) Math.sqrt(first[0] * first[0] + first[1] * first[1] + first[2] * first[2]);
        float mag2 = (float) Math.sqrt(last[0] * last[0] + last[1] * last[1] + last[2] * last[2]);

        if (mag1 == 0 || mag2 == 0) return 0.0f;

        float cosAngle = dot / (mag1 * mag2);
        cosAngle = Math.max(-1.0f, Math.min(1.0f, cosAngle)); // Clamp to valid range

        return (float) Math.toDegrees(Math.acos(cosAngle));
    }

    /**
     * Calculate dominant frequency using simple peak detection
     */
    private float calculateDominantFrequency(List<Float> magnitudes) {
        if (magnitudes.size() < 4) return 0.0f;

        // Simple peak counting approach
        int peaks = 0;
        for (int i = 1; i < magnitudes.size() - 1; i++) {
            if (magnitudes.get(i) > magnitudes.get(i - 1) &&
                magnitudes.get(i) > magnitudes.get(i + 1)) {
                peaks++;
            }
        }

        // Estimate frequency based on peaks (assuming 50Hz sampling rate, 2-second window)
        float samplingRate = 50.0f;
        float windowDuration = magnitudes.size() / samplingRate;
        return peaks / windowDuration;
    }

    /**
     * Calculate mean of a list of values
     */
    private float calculateMean(List<Float> values) {
        if (values.isEmpty()) return 0.0f;

        float sum = 0.0f;
        for (float value : values) {
            sum += value;
        }
        return sum / values.size();
    }

    /**
     * Update fall detection threshold
     */
    public void setFallThreshold(float threshold) {
        this.fallThreshold = Math.max(0.1f, Math.min(1.0f, threshold));
        Log.d(TAG, "Fall threshold updated to: " + this.fallThreshold);
    }

    /**
     * Get current fall detection threshold
     */
    public float getFallThreshold() {
        return fallThreshold;
    }

    /**
     * Learn from a false alarm to improve future detection
     */
    public void learnFromFalseAlarm(SensorDataCollector.SensorDataWindow dataWindow, float confidence) {
        if (dataWindow == null || dataWindow.accelerometerData.isEmpty()) {
            Log.w(TAG, "Cannot learn from false alarm - no data available");
            return;
        }

        // Extract features from the false alarm
        MotionFeatures features = extractMotionFeatures(dataWindow);

        // Let the learning engine learn from this pattern
        learningEngine.learnFromFalseAlarm(features, confidence);

        Log.i(TAG, "Learned from false alarm - confidence: " + confidence);
    }

    /**
     * Get learning statistics
     */
    public AdaptiveLearningEngine.LearningStats getLearningStats() {
        return learningEngine.getLearningStats();
    }

    /**
     * Reset adaptive learning (clear all learned patterns)
     */
    public void resetLearning() {
        learningEngine.resetLearning();
        Log.i(TAG, "TinyML learning reset");
    }

    /**
     * Check if TensorFlow Lite model is loaded
     */
    public boolean isModelLoaded() {
        return isModelLoaded;
    }

    /**
     * Get model information
     */
    public String getModelInfo() {
        if (isModelLoaded && tflite != null) {
            return String.format("TensorFlow Lite model loaded - Input: %d, Output: %d",
                INPUT_SIZE, OUTPUT_SIZE);
        } else {
            return "Rule-based fall detection (TFLite model not available)";
        }
    }

    /**
     * Clean up resources
     */
    public void cleanup() {
        if (tflite != null) {
            tflite.close();
            tflite = null;
        }
        isModelLoaded = false;
        Log.d(TAG, "TinyML processor cleaned up");
    }

    /**
     * Motion features extracted from sensor data
     */
    public static class MotionFeatures {
        final float maxMagnitude;
        final float minMagnitude;
        final float avgMagnitude;
        final float standardDeviation;
        final float maxJerk;
        final float orientationChange;
        final float dominantFrequency;
        final float avgVerticalAccel;
        final float avgHorizontalMagnitude;

        MotionFeatures(float maxMagnitude, float minMagnitude, float avgMagnitude,
                      float standardDeviation, float maxJerk, float orientationChange,
                      float dominantFrequency, float avgVerticalAccel, float avgHorizontalMagnitude) {
            this.maxMagnitude = maxMagnitude;
            this.minMagnitude = minMagnitude;
            this.avgMagnitude = avgMagnitude;
            this.standardDeviation = standardDeviation;
            this.maxJerk = maxJerk;
            this.orientationChange = orientationChange;
            this.dominantFrequency = dominantFrequency;
            this.avgVerticalAccel = avgVerticalAccel;
            this.avgHorizontalMagnitude = avgHorizontalMagnitude;
        }
    }

    /**
     * Result class for fall detection
     */
    public static class FallDetectionResult {
        public final boolean isFall;
        public final float confidence;
        public final String details;
        public final long timestamp;

        public FallDetectionResult(boolean isFall, float confidence, String details) {
            this.isFall = isFall;
            this.confidence = confidence;
            this.details = details;
            this.timestamp = System.currentTimeMillis();
        }

        @Override
        public String toString() {
            return String.format("Fall: %s, Confidence: %.3f, Details: %s",
                isFall, confidence, details);
        }
    }
}
