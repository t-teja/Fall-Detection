package com.tejalabs.falldetection.utils;

import android.content.Context;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;

/**
 * Adaptive Learning Engine for TinyML Fall Detection
 * Learns from false alarms to improve detection accuracy
 */
public class AdaptiveLearningEngine {
    
    private static final String TAG = "AdaptiveLearningEngine";
    
    // Learning parameters
    private static final int MAX_PATTERN_HISTORY = 100;
    private static final float SIMILARITY_THRESHOLD = 0.85f;
    private static final float LEARNING_RATE = 0.1f;
    
    private SharedPreferencesManager prefsManager;
    private List<FalseAlarmPattern> falseAlarmPatterns;
    
    public AdaptiveLearningEngine(Context context) {
        prefsManager = SharedPreferencesManager.getInstance(context);
        falseAlarmPatterns = new ArrayList<>();
        loadLearnedPatterns();
    }
    
    /**
     * Learn from a false alarm by storing the motion pattern
     */
    public void learnFromFalseAlarm(TinyMLProcessor.MotionFeatures features, float confidence) {
        Log.i(TAG, "Learning from false alarm - confidence: " + confidence);
        
        // Create a new false alarm pattern
        FalseAlarmPattern pattern = new FalseAlarmPattern(
            features.maxMagnitude,
            features.minMagnitude,
            features.avgMagnitude,
            features.standardDeviation,
            features.maxJerk,
            features.orientationChange,
            features.dominantFrequency,
            confidence,
            System.currentTimeMillis()
        );
        
        // Add to learned patterns
        falseAlarmPatterns.add(pattern);
        
        // Limit history size
        if (falseAlarmPatterns.size() > MAX_PATTERN_HISTORY) {
            falseAlarmPatterns.remove(0);
        }
        
        // Save learned patterns
        saveLearnedPatterns();
        
        Log.i(TAG, "False alarm pattern learned. Total patterns: " + falseAlarmPatterns.size());
    }
    
    /**
     * Check if current motion pattern is similar to known false alarms
     */
    public boolean isSimilarToFalseAlarm(TinyMLProcessor.MotionFeatures features, float confidence) {
        if (falseAlarmPatterns.isEmpty()) {
            return false;
        }
        
        // Check similarity with all learned false alarm patterns
        for (FalseAlarmPattern pattern : falseAlarmPatterns) {
            float similarity = calculateSimilarity(features, confidence, pattern);
            
            if (similarity > SIMILARITY_THRESHOLD) {
                Log.d(TAG, String.format("Similar to false alarm pattern (%.2f similarity)", similarity));
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Calculate similarity between current features and a false alarm pattern
     */
    private float calculateSimilarity(TinyMLProcessor.MotionFeatures features, float confidence, FalseAlarmPattern pattern) {
        // Normalize differences to 0-1 scale
        float maxMagDiff = Math.abs(features.maxMagnitude - pattern.maxMagnitude) / 50.0f;
        float minMagDiff = Math.abs(features.minMagnitude - pattern.minMagnitude) / 20.0f;
        float avgMagDiff = Math.abs(features.avgMagnitude - pattern.avgMagnitude) / 30.0f;
        float stdDevDiff = Math.abs(features.standardDeviation - pattern.standardDeviation) / 15.0f;
        float jerkDiff = Math.abs(features.maxJerk - pattern.maxJerk) / 25.0f;
        float orientDiff = Math.abs(features.orientationChange - pattern.orientationChange) / 180.0f;
        float freqDiff = Math.abs(features.dominantFrequency - pattern.dominantFrequency) / 30.0f;
        float confDiff = Math.abs(confidence - pattern.confidence);
        
        // Calculate weighted similarity (lower difference = higher similarity)
        float similarity = 1.0f - (
            maxMagDiff * 0.20f +
            minMagDiff * 0.15f +
            avgMagDiff * 0.15f +
            stdDevDiff * 0.15f +
            jerkDiff * 0.15f +
            orientDiff * 0.10f +
            freqDiff * 0.05f +
            confDiff * 0.05f
        );
        
        return Math.max(0.0f, Math.min(1.0f, similarity));
    }
    
    /**
     * Get adaptive confidence adjustment based on learned patterns
     */
    public float getConfidenceAdjustment(TinyMLProcessor.MotionFeatures features, float originalConfidence) {
        if (falseAlarmPatterns.isEmpty()) {
            return originalConfidence;
        }
        
        float maxSimilarity = 0.0f;
        for (FalseAlarmPattern pattern : falseAlarmPatterns) {
            float similarity = calculateSimilarity(features, originalConfidence, pattern);
            maxSimilarity = Math.max(maxSimilarity, similarity);
        }
        
        // Reduce confidence if similar to false alarms
        if (maxSimilarity > 0.5f) {
            float reduction = maxSimilarity * LEARNING_RATE;
            float adjustedConfidence = originalConfidence * (1.0f - reduction);
            
            Log.d(TAG, String.format("Confidence adjusted: %.3f -> %.3f (similarity: %.2f)", 
                originalConfidence, adjustedConfidence, maxSimilarity));
            
            return adjustedConfidence;
        }
        
        return originalConfidence;
    }
    
    /**
     * Get learning statistics
     */
    public LearningStats getLearningStats() {
        int totalPatterns = falseAlarmPatterns.size();
        int recentPatterns = 0;
        long oneWeekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L);
        
        for (FalseAlarmPattern pattern : falseAlarmPatterns) {
            if (pattern.timestamp > oneWeekAgo) {
                recentPatterns++;
            }
        }
        
        return new LearningStats(totalPatterns, recentPatterns);
    }
    
    /**
     * Clear all learned patterns (reset learning)
     */
    public void resetLearning() {
        falseAlarmPatterns.clear();
        saveLearnedPatterns();
        Log.i(TAG, "Learning patterns reset");
    }
    
    /**
     * Load learned patterns from preferences
     */
    private void loadLearnedPatterns() {
        // For now, we'll start fresh each time
        // In a real implementation, you'd serialize/deserialize the patterns
        falseAlarmPatterns.clear();
        Log.d(TAG, "Learned patterns loaded (starting fresh)");
    }
    
    /**
     * Save learned patterns to preferences
     */
    private void saveLearnedPatterns() {
        // For now, patterns are only stored in memory
        // In a real implementation, you'd serialize the patterns to SharedPreferences or a database
        Log.d(TAG, "Learned patterns saved (memory only)");
    }
    
    /**
     * False alarm pattern data structure
     */
    private static class FalseAlarmPattern {
        final float maxMagnitude;
        final float minMagnitude;
        final float avgMagnitude;
        final float standardDeviation;
        final float maxJerk;
        final float orientationChange;
        final float dominantFrequency;
        final float confidence;
        final long timestamp;
        
        FalseAlarmPattern(float maxMagnitude, float minMagnitude, float avgMagnitude,
                         float standardDeviation, float maxJerk, float orientationChange,
                         float dominantFrequency, float confidence, long timestamp) {
            this.maxMagnitude = maxMagnitude;
            this.minMagnitude = minMagnitude;
            this.avgMagnitude = avgMagnitude;
            this.standardDeviation = standardDeviation;
            this.maxJerk = maxJerk;
            this.orientationChange = orientationChange;
            this.dominantFrequency = dominantFrequency;
            this.confidence = confidence;
            this.timestamp = timestamp;
        }
    }
    
    /**
     * Learning statistics
     */
    public static class LearningStats {
        public final int totalPatterns;
        public final int recentPatterns;
        
        LearningStats(int totalPatterns, int recentPatterns) {
            this.totalPatterns = totalPatterns;
            this.recentPatterns = recentPatterns;
        }
    }
}
