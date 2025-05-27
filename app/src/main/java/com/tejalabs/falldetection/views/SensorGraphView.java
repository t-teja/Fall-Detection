package com.tejalabs.falldetection.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple custom view to display sensor data as a real-time graph
 * Useful for development and debugging fall detection algorithms
 */
public class SensorGraphView extends View {
    
    private static final String TAG = "SensorGraphView";
    
    // Graph configuration
    private static final int MAX_DATA_POINTS = 100;
    private static final float GRAPH_MARGIN = 50f;
    private static final float LINE_WIDTH = 3f;
    
    // Data storage
    private List<Float> accelerometerMagnitude;
    private List<Float> gyroscopeMagnitude;
    private List<Long> timestamps;
    
    // Paint objects for drawing
    private Paint accelPaint;
    private Paint gyroPaint;
    private Paint gridPaint;
    private Paint textPaint;
    private Paint backgroundPaint;
    
    // Graph bounds
    private float minValue = 0f;
    private float maxValue = 20f;
    private boolean autoScale = true;
    
    public SensorGraphView(Context context) {
        super(context);
        init();
    }
    
    public SensorGraphView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public SensorGraphView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    /**
     * Initialize the view and paint objects
     */
    private void init() {
        accelerometerMagnitude = new ArrayList<>();
        gyroscopeMagnitude = new ArrayList<>();
        timestamps = new ArrayList<>();
        
        // Initialize paint objects
        accelPaint = new Paint();
        accelPaint.setColor(Color.RED);
        accelPaint.setStrokeWidth(LINE_WIDTH);
        accelPaint.setStyle(Paint.Style.STROKE);
        accelPaint.setAntiAlias(true);
        
        gyroPaint = new Paint();
        gyroPaint.setColor(Color.BLUE);
        gyroPaint.setStrokeWidth(LINE_WIDTH);
        gyroPaint.setStyle(Paint.Style.STROKE);
        gyroPaint.setAntiAlias(true);
        
        gridPaint = new Paint();
        gridPaint.setColor(Color.GRAY);
        gridPaint.setStrokeWidth(1f);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setAlpha(100);
        
        textPaint = new Paint();
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(24f);
        textPaint.setAntiAlias(true);
        
        backgroundPaint = new Paint();
        backgroundPaint.setColor(Color.WHITE);
        backgroundPaint.setStyle(Paint.Style.FILL);
    }
    
    /**
     * Add new sensor data point
     */
    public void addDataPoint(float accelMagnitude, float gyroMagnitude) {
        long currentTime = System.currentTimeMillis();
        
        // Add new data points
        accelerometerMagnitude.add(accelMagnitude);
        gyroscopeMagnitude.add(gyroMagnitude);
        timestamps.add(currentTime);
        
        // Remove old data points if we exceed the maximum
        while (accelerometerMagnitude.size() > MAX_DATA_POINTS) {
            accelerometerMagnitude.remove(0);
            gyroscopeMagnitude.remove(0);
            timestamps.remove(0);
        }
        
        // Update scale if auto-scaling is enabled
        if (autoScale) {
            updateScale();
        }
        
        // Trigger redraw
        invalidate();
    }
    
    /**
     * Update the scale based on current data
     */
    private void updateScale() {
        if (accelerometerMagnitude.isEmpty()) {
            return;
        }
        
        float dataMin = Float.MAX_VALUE;
        float dataMax = Float.MIN_VALUE;
        
        // Find min/max from both datasets
        for (float value : accelerometerMagnitude) {
            dataMin = Math.min(dataMin, value);
            dataMax = Math.max(dataMax, value);
        }
        
        for (float value : gyroscopeMagnitude) {
            dataMin = Math.min(dataMin, value);
            dataMax = Math.max(dataMax, value);
        }
        
        // Add some padding
        float padding = (dataMax - dataMin) * 0.1f;
        minValue = Math.max(0, dataMin - padding);
        maxValue = dataMax + padding;
        
        // Ensure minimum range
        if (maxValue - minValue < 5f) {
            maxValue = minValue + 5f;
        }
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        if (accelerometerMagnitude.isEmpty()) {
            drawEmptyState(canvas);
            return;
        }
        
        // Draw background
        canvas.drawRect(0, 0, getWidth(), getHeight(), backgroundPaint);
        
        // Calculate drawing area
        float graphWidth = getWidth() - 2 * GRAPH_MARGIN;
        float graphHeight = getHeight() - 2 * GRAPH_MARGIN;
        
        // Draw grid
        drawGrid(canvas, graphWidth, graphHeight);
        
        // Draw data lines
        drawDataLine(canvas, accelerometerMagnitude, accelPaint, graphWidth, graphHeight);
        drawDataLine(canvas, gyroscopeMagnitude, gyroPaint, graphWidth, graphHeight);
        
        // Draw legend
        drawLegend(canvas);
        
        // Draw scale labels
        drawScaleLabels(canvas, graphHeight);
    }
    
    /**
     * Draw empty state message
     */
    private void drawEmptyState(Canvas canvas) {
        canvas.drawRect(0, 0, getWidth(), getHeight(), backgroundPaint);
        
        String message = "Waiting for sensor data...";
        float textWidth = textPaint.measureText(message);
        float x = (getWidth() - textWidth) / 2;
        float y = getHeight() / 2;
        
        canvas.drawText(message, x, y, textPaint);
    }
    
    /**
     * Draw grid lines
     */
    private void drawGrid(Canvas canvas, float graphWidth, float graphHeight) {
        // Vertical grid lines
        for (int i = 0; i <= 10; i++) {
            float x = GRAPH_MARGIN + (graphWidth * i / 10);
            canvas.drawLine(x, GRAPH_MARGIN, x, GRAPH_MARGIN + graphHeight, gridPaint);
        }
        
        // Horizontal grid lines
        for (int i = 0; i <= 5; i++) {
            float y = GRAPH_MARGIN + (graphHeight * i / 5);
            canvas.drawLine(GRAPH_MARGIN, y, GRAPH_MARGIN + graphWidth, y, gridPaint);
        }
    }
    
    /**
     * Draw a data line
     */
    private void drawDataLine(Canvas canvas, List<Float> data, Paint paint, float graphWidth, float graphHeight) {
        if (data.size() < 2) {
            return;
        }
        
        Path path = new Path();
        boolean firstPoint = true;
        
        for (int i = 0; i < data.size(); i++) {
            float x = GRAPH_MARGIN + (graphWidth * i / (MAX_DATA_POINTS - 1));
            float normalizedValue = (data.get(i) - minValue) / (maxValue - minValue);
            float y = GRAPH_MARGIN + graphHeight - (normalizedValue * graphHeight);
            
            if (firstPoint) {
                path.moveTo(x, y);
                firstPoint = false;
            } else {
                path.lineTo(x, y);
            }
        }
        
        canvas.drawPath(path, paint);
    }
    
    /**
     * Draw legend
     */
    private void drawLegend(Canvas canvas) {
        float legendY = 30f;
        
        // Accelerometer legend
        canvas.drawLine(10f, legendY, 40f, legendY, accelPaint);
        canvas.drawText("Accelerometer", 50f, legendY + 5f, textPaint);
        
        // Gyroscope legend
        legendY += 30f;
        canvas.drawLine(10f, legendY, 40f, legendY, gyroPaint);
        canvas.drawText("Gyroscope", 50f, legendY + 5f, textPaint);
    }
    
    /**
     * Draw scale labels
     */
    private void drawScaleLabels(Canvas canvas, float graphHeight) {
        for (int i = 0; i <= 5; i++) {
            float value = maxValue - ((maxValue - minValue) * i / 5);
            float y = GRAPH_MARGIN + (graphHeight * i / 5) + 5f;
            canvas.drawText(String.format("%.1f", value), 5f, y, textPaint);
        }
    }
    
    /**
     * Clear all data
     */
    public void clearData() {
        accelerometerMagnitude.clear();
        gyroscopeMagnitude.clear();
        timestamps.clear();
        invalidate();
    }
    
    /**
     * Set auto-scaling enabled/disabled
     */
    public void setAutoScale(boolean autoScale) {
        this.autoScale = autoScale;
    }
    
    /**
     * Set manual scale range
     */
    public void setScale(float minValue, float maxValue) {
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.autoScale = false;
        invalidate();
    }
}
