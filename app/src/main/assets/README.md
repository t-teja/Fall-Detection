# TensorFlow Lite Model

This directory should contain the TensorFlow Lite model file for fall detection.

## Required File:
- `fall_detection_model.tflite` - The trained TensorFlow Lite model for fall detection

## Note:
Currently, the app uses a fallback rule-based detection algorithm when the model file is not present.

To add a real model:
1. Train a fall detection model using TensorFlow
2. Convert it to TensorFlow Lite format (.tflite)
3. Place the file in this directory as `fall_detection_model.tflite`
4. The app will automatically use the ML model instead of the fallback algorithm
