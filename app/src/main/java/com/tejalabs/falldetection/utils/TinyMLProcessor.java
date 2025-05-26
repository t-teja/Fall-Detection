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
import java.util.HashMap;
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
    
    public TinyMLProcessor(Context context) {
        prefsManager = SharedPreferencesManager.getInstance(context);
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
     * Process data using rule-based fall detection
     */
    private FallDetectionResult processWithRuleBasedDetection(SensorDataCollector.SensorDataWindow dataWindow) {
        if (dataWindow.accelerometerData.isEmpty()) {
            return new FallDetectionResult(false, 0.0f, "No accelerometer data");
        }
        
        // Calculate acceleration magnitudes
        float maxMagnitude = 0.0f;
        float minMagnitude = Float.MAX_VALUE;
        float totalMagnitude = 0.0f;
        
        for (Float[] sample : dataWindow.accelerometerData) {
            float magnitude = (float) Math.sqrt(
                sample[0] * sample[0] + 
                sample[1] * sample[1] + 
                sample[2] * sample[2]
            );
            
            maxMagnitude = Math.max(maxMagnitude, magnitude);
            minMagnitude = Math.min(minMagnitude, magnitude);
            totalMagnitude += magnitude;
        }
        
        float avgMagnitude = totalMagnitude / dataWindow.accelerometerData.size();
        
        // Get sensitivity threshold from settings
        float sensitivityThreshold = prefsManager.getSensitivityThreshold();
        
        // Fall detection logic
        boolean hasImpact = maxMagnitude > (15.0f * sensitivityThreshold);
        boolean hasFreeFall = minMagnitude < (3.0f * sensitivityThreshold);
        boolean hasVariation = (maxMagnitude - minMagnitude) > (10.0f * sensitivityThreshold);
        
        // Calculate confidence based on thresholds
        float confidence = 0.0f;
        if (hasImpact && hasFreeFall && hasVariation) {
            confidence = Math.min(1.0f, 
                (maxMagnitude / 20.0f) * 0.4f + 
                ((20.0f - minMagnitude) / 20.0f) * 0.3f + 
                ((maxMagnitude - minMagnitude) / 20.0f) * 0.3f
            );
        }
        
        boolean isFall = confidence > 0.6f;
        
        String details = String.format(
            "Rule-based detection - Max: %.2f, Min: %.2f, Avg: %.2f, Confidence: %.3f",
            maxMagnitude, minMagnitude, avgMagnitude, confidence
        );
        
        return new FallDetectionResult(isFall, confidence, details);
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
