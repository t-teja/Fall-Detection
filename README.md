# Fall Detection App for Elderly Care

An Android application that uses TinyML (TensorFlow Lite) and foreground services to detect falls in elderly people and automatically alert emergency contacts. This app is designed to provide peace of mind for families and help save lives during medical emergencies.

## 🎯 Features

### Core Functionality
- **Real-time Fall Detection**: Uses phone sensors (accelerometer, gyroscope) with TinyML for accurate fall detection
- **Foreground Service**: Continuous monitoring even when app is in background
- **Emergency Response**: Automatic SMS alerts and emergency calls to configured contacts
- **GPS Location Sharing**: Includes precise location in emergency messages
- **Countdown Timer**: 30-second countdown to prevent false positive alerts

### User Experience
- **Elderly-Friendly UI**: Large buttons, clear text, simple navigation
- **Battery Optimized**: Efficient algorithms to preserve device battery
- **Auto-Start**: Automatically starts monitoring after device reboot
- **Customizable Settings**: Adjustable sensitivity and emergency response options

### Safety Features
- **Multiple Emergency Contacts**: Support for multiple emergency contacts
- **Test Mode**: Test emergency procedures without sending real alerts
- **False Positive Handling**: User can cancel alerts within countdown period
- **Data Logging**: Comprehensive logging for analysis and improvement

## 🏗️ Architecture

### Technology Stack
- **Platform**: Android (API 24+)
- **Language**: Java
- **ML Framework**: TensorFlow Lite
- **Location Services**: Google Play Services
- **Background Processing**: Foreground Services
- **Data Storage**: SharedPreferences + File-based logging

### Key Components
- **FallDetectionService**: Core foreground service for continuous monitoring
- **SensorDataCollector**: Handles sensor data collection and preprocessing
- **TinyMLProcessor**: TensorFlow Lite model integration for fall detection
- **EmergencyManager**: Manages emergency response procedures
- **ContactManager**: Emergency contact management
- **LocationService**: GPS location tracking for emergency alerts

## 📱 Screenshots

*Screenshots will be added once the UI is fully implemented*

## 🚀 Getting Started

### Prerequisites
- Android Studio Arctic Fox or later
- Android SDK 34
- Device with Android 7.0 (API 24) or higher
- Physical device recommended for testing (sensors required)


### Required Permissions
The app requires the following permissions:
- **Sensors**: Body sensors, activity recognition
- **Location**: Fine and coarse location access
- **Communication**: SMS and phone call permissions
- **System**: Foreground service, wake lock, notifications

## 🔧 Configuration

### First-Time Setup
1. Launch the app and grant all required permissions
2. Add emergency contacts (at least one required)
3. Configure sensitivity settings (optional)
4. Test emergency procedures
5. Start fall detection monitoring

### Emergency Contacts
- Add multiple emergency contacts with names and phone numbers
- Set one contact as primary for emergency calls
- Test contacts using the "Test Emergency Alert" feature

### Sensitivity Settings
- **Level 1**: Very Low (fewer false positives, may miss some falls)
- **Level 3**: Medium (default, balanced detection)
- **Level 5**: Very High (more sensitive, may have more false positives)

## 📊 How It Works

### Fall Detection Algorithm
1. **Sensor Data Collection**: Continuously collects accelerometer and gyroscope data
2. **Data Preprocessing**: Filters and normalizes sensor readings
3. **Feature Extraction**: Calculates statistical features from sensor windows
4. **ML Inference**: Uses TensorFlow Lite model to classify fall patterns
5. **Threshold Checking**: Applies confidence thresholds based on sensitivity settings

### Emergency Response Flow
1. **Fall Detected**: Algorithm detects potential fall with high confidence
2. **Countdown Timer**: 30-second countdown allows user to cancel false positives
3. **Location Acquisition**: Gets current GPS coordinates
4. **SMS Alerts**: Sends emergency messages to all configured contacts
5. **Emergency Call**: Optionally calls primary emergency contact
6. **Logging**: Records event for analysis and improvement

## 🛠️ Development

### Project Structure
```
app/src/main/java/com/tejalabs/falldetection/
├── MainActivity.java                 # Main dashboard
├── activities/                       # Additional activities
├── services/                         # Background services
├── utils/                           # Utility classes
├── receivers/                       # Broadcast receivers
└── ml/                             # Machine learning components
```

### Key Classes
- **FallDetectionService**: Core monitoring service
- **SensorDataCollector**: Sensor data handling
- **TinyMLProcessor**: ML model integration
- **EmergencyManager**: Emergency response coordination
- **ContactManager**: Emergency contact management

### Building for Production
1. Update version in `build.gradle`
2. Configure signing keys
3. Build release APK: `./gradlew assembleRelease`
4. Test thoroughly on multiple devices

## 🧪 Testing

### Unit Testing
```bash
./gradlew test
```

### Integration Testing
```bash
./gradlew connectedAndroidTest
```

### Manual Testing Checklist
- [ ] Sensor data collection works
- [ ] Fall detection triggers appropriately
- [ ] Emergency contacts receive SMS alerts
- [ ] GPS location is included in messages
- [ ] Service survives device reboot
- [ ] Battery usage is acceptable
- [ ] UI is accessible for elderly users

## 📈 Performance

### Battery Optimization
- Efficient sensor sampling rates
- Optimized ML model inference
- Proper wake lock management
- Background processing limitations

### Accuracy Metrics
- **Target Accuracy**: >95% true positive rate
- **False Positive Rate**: <2% per day
- **Response Time**: <3 seconds from fall to alert

## 🤝 Contributing

We welcome contributions! Please see our [Contributing Guidelines](CONTRIBUTING.md) for details.

### Development Setup
1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests for new functionality
5. Submit a pull request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🆘 Support

### Getting Help
- **Issues**: Report bugs on [GitHub Issues](https://github.com/t-teja/Fall-Detection/issues)
- **Discussions**: Join discussions on [GitHub Discussions](https://github.com/t-teja/Fall-Detection/discussions)


### Known Issues
- TensorFlow Lite model not included (placeholder implementation)
- Settings and Emergency Contacts activities are placeholders
- Some permissions may need manual enabling on certain devices

## 🔮 Roadmap

### Version 1.1 (Planned)
- [ ] Complete Settings UI implementation
- [ ] Full Emergency Contacts management
- [ ] Custom ML model training
- [ ] Improved fall detection algorithms

### Version 1.2 (Future)
- [ ] Smartwatch integration
- [ ] Cloud dashboard for family monitoring
- [ ] Voice command support
- [ ] Health metrics tracking

### Version 2.0 (Vision)
- [ ] AI-powered health insights
- [ ] Integration with medical devices
- [ ] Telemedicine features
- [ ] Multi-language support

---

**Made with ❤️ for elderly care and safety**

**Repository**: https://github.com/t-teja/Fall-Detection.git  
**Developer**: TejaLabs  
**Version**: 1.0.0-alpha
