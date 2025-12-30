# 📱 APK Deployment Guide - Guild of Smiths with AI Assistant

## Version Information
- **App Version**: 0.2.0-ai-alpha
- **Version Code**: 2
- **AI Features**: Embedded Assistant (Standard + Hybrid modes)

## Files Generated

### Debug APK (Development)
- **Location**: `app/build/outputs/apk/debug/app-debug.apk`
- **Purpose**: Development testing
- **Signing**: Debug keystore (auto-generated)

### Release APK (Production)
- **Location**: `app/build/outputs/apk/release/app-release.apk`
- **Purpose**: Production deployment
- **Signing**: Requires keystore configuration

## Building APKs

### Using Build Scripts
```bash
# Linux/Mac
./build-apk.sh

# Windows
build-apk.bat
```

### Manual Gradle Commands
```bash
# Debug build
./gradlew assembleDebug

# Release build (requires keystore)
./gradlew assembleRelease

# Install on connected device
./gradlew installDebug
```

## Phone Deployment Options

### Method 1: Direct ADB Install
```bash
# Connect phone via USB
adb devices

# Install debug APK
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Install release APK
adb install -r app/build/outputs/apk/release/app-release.apk
```

### Method 2: File Transfer
1. Copy APK file to phone storage
2. Enable "Install from unknown sources" in phone settings
3. Open file manager and tap APK to install
4. Grant requested permissions during installation

### Method 3: Wireless ADB (Android 11+)
```bash
# Enable wireless debugging on phone
adb pair <phone_ip>:<port> <pairing_code>
adb connect <phone_ip>:<port>
adb install -r app-debug.apk
```

## Phone Requirements

### Minimum Specifications
- **Android Version**: API 26 (Android 8.0) or higher
- **RAM**: 2GB minimum, 4GB recommended
- **Storage**: 100MB free space
- **Hardware**: BLE support recommended (but not required)

### Recommended for AI Features
- **Battery**: 3000mAh or larger for Hybrid mode
- **Internet**: WiFi/4G/5G for cloud AI features
- **Storage**: 500MB+ free for AI models (optional download)

## AI Feature Configuration

### First Launch Setup
1. **Grant Permissions**:
   - Bluetooth/BLE access
   - Location access (for BLE scanning)
   - Camera/Microphone (for media features)
   - Notifications (for foreground service)

2. **AI Assistant Setup**:
   - Open Settings → AI Assistant
   - Choose mode: Standard (recommended) or Hybrid
   - AI automatically observes app activity

### AI Modes
- **Standard Mode**: Always-on, local AI, zero battery drain
- **Hybrid Mode**: Local + cloud AI when conditions met

## Testing on Phones

### Basic Functionality Test
1. Install APK on phone
2. Grant all permissions
3. Send test messages to verify mesh/chat paths
4. Test AI by typing "@AI checklist" or "@AI clock in"

### AI Feature Testing
1. **Standard Mode**: Works offline, responds to common commands
2. **Hybrid Mode**: Requires internet, enhances responses when available
3. **Battery Testing**: AI degrades gracefully on low battery
4. **Offline Testing**: Queue responses when offline, sync when connected

## Troubleshooting

### Installation Issues
- **"App not installed"**: Clear app data/cache, try again
- **Permissions denied**: Go to Settings → Apps → TradeMesh → Permissions
- **BLE not working**: Ensure Bluetooth is enabled, try reboot

### AI Issues
- **AI not responding**: Check battery level (>20% for Hybrid mode)
- **No internet**: AI falls back to Standard mode automatically
- **Slow responses**: Ensure device isn't overheating

## Production Deployment

### Signing Configuration
Create `android/keystore.properties`:
```
storeFile=../keystore.jks
storePassword=your_store_password
keyAlias=your_key_alias
keyPassword=your_key_password
```

### Google Play Deployment
1. Build signed release APK/bundle
2. Upload to Google Play Console
3. Configure store listing with AI features mentioned
4. Set up beta testing if desired

---

**Guild of Smiths – Built for the trades, enhanced by AI.**
