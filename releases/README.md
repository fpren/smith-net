# 📱 Smith Net APK Releases

This folder contains built APK files for the Smith Net application.

## Latest Release

### Smith Net v0.5.0 (Debug Build)
- **File**: `apks/smith-net-v0.5.0-debug.apk`
- **Version Code**: 5
- **Version Name**: 0.5.0
- **Build Date**: January 4, 2026

#### Features Included:
- ✅ **SmithNet Dashboard** - Comprehensive job management interface
- ✅ **Specialized Job Cards** - PENDING, LIVE, READY_TO_CLOSE, ARCHIVED
- ✅ **AI Assistant** - Ambient AI with Standard and Hybrid modes
- ✅ **BLE Mesh Communication** - Local device-to-device messaging
- ✅ **Supabase Integration** - Cloud chat and data synchronization
- ✅ **Time Tracking** - Built-in job time management
- ✅ **Invoice Generation** - Automated invoicing system
- ✅ **Analytics & Planning** - Advanced project management tools

## Installation Instructions

### Direct Install (Recommended)
```bash
# Connect Android device via USB
adb install -r releases/apks/smith-net-v0.5.0-debug.apk

# Or copy file to device and install manually
```

### Phone Requirements
- **Android Version**: API 26+ (Android 8.0 or higher)
- **RAM**: 2GB minimum, 4GB recommended
- **Storage**: 100MB free space
- **BLE Support**: Recommended for mesh features

## AI Features Configuration

After installation:
1. Grant all requested permissions (Bluetooth, Location, Notifications)
2. Open Settings → AI Assistant
3. Choose mode: **Standard** (recommended) or **Hybrid**
4. AI will automatically observe app activity

### AI Modes
- **Standard Mode**: Always-on local AI, zero battery drain
- **Hybrid Mode**: Local + cloud AI when internet available

## Build Information

Built with:
- Android Gradle Plugin 8.x
- Kotlin 1.9.21
- Target SDK 34
- Min SDK 26
- NDK 25.2.9519653 (for llama.cpp integration)

## Development

To build your own APK:
```bash
cd android
./gradlew assembleDebug    # Debug build
./gradlew assembleRelease  # Release build (requires keystore)
```

---
**Guild of Smiths – Built for the trades, enhanced by AI.**