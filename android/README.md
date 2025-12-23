# TradeMesh - Phase 0 Android Prototype

Guild of Smiths dual-path communication system prototype for Android-to-Android validation.

## Overview

This Phase 0 prototype validates the dual-path communication architecture:
- **Mesh Path (BLE)**: Local, offline, real-time short messages
- **Chat Path (IP)**: Persistent, ordered, supports longer content

## Project Structure

```
app/src/main/java/com/guildofsmiths/trademesh/
├── data/
│   ├── Message.kt           # Core message data model
│   └── MessageRepository.kt # In-memory message storage
├── engine/
│   └── BoundaryEngine.kt    # Path routing & sync logic
├── service/
│   ├── MeshService.kt       # BLE advertise/scan service
│   └── ChatManager.kt       # IP chat stub (Phase 0)
├── ui/
│   ├── ConversationScreen.kt    # Console-style message UI
│   ├── ConversationViewModel.kt # UI state management
│   └── theme/Theme.kt           # Dark console theme
├── MainActivity.kt          # Entry point, permissions
└── TradeMeshApplication.kt  # Application class
```

## Building

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or later
- Android SDK 34
- JDK 17

### Setup
1. Clone the repository
2. Copy `local.properties.example` to `local.properties`
3. Update `sdk.dir` to point to your Android SDK
4. Open in Android Studio and sync Gradle

### Build Commands
```bash
# Debug build
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug
```

## Permissions Required

### Android 12+ (API 31+)
- `BLUETOOTH_SCAN` - BLE scanning
- `BLUETOOTH_ADVERTISE` - BLE advertising
- `BLUETOOTH_CONNECT` - BLE connections
- `POST_NOTIFICATIONS` - Foreground service notification

### Android < 12
- `ACCESS_FINE_LOCATION` - Required for BLE scanning
- `BLUETOOTH` / `BLUETOOTH_ADMIN` - Legacy BLE permissions

## Architecture

### BoundaryEngine
Central routing singleton that:
- Determines path based on connectivity (`shouldUseMesh()`)
- Routes messages to appropriate service
- Triggers sync when connectivity restores

### MeshService
Foreground service for BLE operations:
- **Scanning**: Low-power mode, 30-second idle timeout
- **Advertising**: Non-connectable, low-power broadcast
- **Queue**: ArrayDeque for pending outbound messages

### Message Format
```kotlin
data class Message(
    val id: String,          // UUID
    val senderId: String,    // Device/user ID
    val senderName: String,  // Display name
    val timestamp: Long,     // Unix millis
    val content: String,     // Text content
    val isMeshOrigin: Boolean // Source path indicator
)
```

## UI Design

Console-style single-column log:
- Monospaced font throughout
- Tight 4dp vertical spacing
- `[mesh]` prefix for mesh-origin messages
- No bubbles, avatars, or cards
- Dark theme (#0D1117 background)

## Testing on Real Devices

1. Install on two or more Android devices
2. Grant all requested permissions
3. Disable WiFi on one device to force mesh path
4. Send messages and observe `[mesh]` indicators
5. Re-enable WiFi and observe sync behavior

## Battery Considerations

- BLE scan mode: `SCAN_MODE_LOW_POWER`
- BLE advertise mode: `ADVERTISE_MODE_LOW_POWER`
- Scan timeout: 30 seconds idle
- Non-connectable advertising (no connection overhead)

## Known Limitations (Phase 0)

- BLE payload limited to ~24 bytes in advertisement data
- No message chunking for larger payloads
- Chat backend is a logging stub
- No persistent storage (in-memory only)
- Single conversation/channel

## Next Steps (Phase 1+)

- [ ] GATT-based messaging for larger payloads
- [ ] Room database for persistence
- [ ] Real chat backend integration
- [ ] Multi-channel support
- [ ] iOS implementation
