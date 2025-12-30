# TradeMesh - Phase 0 Android Prototype

Guild of Smiths dual-path communication system prototype for Android-to-Android validation.

## Overview

This Phase 0 prototype validates the dual-path communication architecture with embedded AI assistance:
- **Mesh Path (BLE)**: Local, offline, real-time short messages
- **Chat Path (IP)**: Persistent, ordered, supports longer content
- **AI Assistant**: Ambient, contextual help without explicit commands

## Project Structure

```
app/src/main/java/com/guildofsmiths/trademesh/
├── ai/                       # Embedded AI Assistant
│   ├── AIRouter.kt           # AI routing & mode management
│   ├── AmbientEventHub.kt    # Event observation (no polling)
│   ├── AmbientRuleEngine.kt  # Standard Mode rule-based AI
│   ├── RuleBasedFallback.kt  # Pre-defined responses
│   ├── BatteryGate.kt        # Battery/thermal gating
│   ├── OfflineQueueManager.kt # Queuing & sync
│   ├── SubAgents.kt          # Contextual AI helpers
│   ├── LlamaInference.kt     # On-device LLM (Qwen3)
│   ├── ResponseCache.kt      # Response caching
│   └── CueDetector.kt        # Intent detection
├── data/
│   ├── Message.kt            # Core message data model
│   ├── MessageRepository.kt  # In-memory message storage
│   └── UserPreferences.kt    # Settings & AI mode config
├── engine/
│   └── BoundaryEngine.kt     # Path routing & sync logic
├── service/
│   ├── MeshService.kt        # BLE advertise/scan service
│   ├── ChatManager.kt        # IP chat stub (Phase 0)
│   └── MediaHelper.kt        # Media handling
├── ui/
│   ├── ConversationScreen.kt     # Console-style message UI
│   ├── ConversationViewModel.kt  # UI state management
│   ├── SettingsScreen.kt         # App settings + AI config
│   └── theme/Theme.kt            # Dark console theme
├── MainActivity.kt           # Entry point, permissions
└── TradeMeshApplication.kt   # Application class
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
# Debug build (includes AI features)
./gradlew assembleDebug

# Release build (optimized for production)
./gradlew assembleRelease

# Install debug version on connected device
./gradlew installDebug

# Install release version on connected device
./gradlew installRelease
```

### AI Feature Configuration
The app includes embedded AI assistant with two modes:
- **Standard Mode**: Local rule-based AI, always available, zero battery drain
- **Hybrid Mode**: Local + cloud AI when connected and charged

AI settings are configured in-app via Settings → AI Assistant.

### APK Deployment
See `APK_DEPLOYMENT.md` for detailed instructions on building and installing the APK with AI features on phones.

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

## AI Assistant Architecture

### Ambient Design Principles
- **No Invocation**: AI observes and assists contextually without explicit commands
- **Battery-Aware**: Automatically degrades based on battery, thermal, and connectivity
- **Mesh-Friendly**: Responses use compressed payloads suitable for BLE transport
- **Offline-First**: Local rule-based AI always available, cloud enhancement optional

### AI Modes

#### Standard Mode (Default)
- **Always-on, local rule-based AI**
- **Zero idle battery drain**
- **Deterministic pattern matching** for:
  - Clock in/out confirmations
  - Material request acknowledgments
  - Trade-specific checklists (electrical, plumbing, HVAC, carpentry, painting)
  - Non-English text translation (cached common phrases)
  - Job status updates

#### Hybrid Mode (Optional)
- **Local rules + cloud AI enhancement**
- **Gated conditions**: Internet + battery (>20%) + thermal state OK
- **Graceful degradation** to Standard when conditions not met
- **Offline queuing** - responses sync when connectivity returns

### AI Event Sources
The AI ambiently observes these events via Flows (no polling):
- Incoming mesh/IP messages
- Time entry creations/edits
- Job/checklist views/edits
- Screen navigation changes
- Connectivity state changes
- Battery level changes
- Location/geofence events
- App lifecycle events

### Technical Implementation
- **AmbientEventHub**: Centralized event observation using Kotlin Flows
- **AmbientRuleEngine**: Lightweight pattern matching with confidence scoring
- **AIRouter**: Mode management and battery/thermal gating
- **OfflineQueueManager**: Queuing and sync when offline
- **BatteryGate**: Multi-factor gating (battery, thermal, power save mode)

## UI Design

Console-style single-column log:
- Monospaced font throughout
- Tight 4dp vertical spacing
- `[mesh]` prefix for mesh-origin messages
- No bubbles, avatars, or cards
- Dark theme (#0D1117 background)

## Testing on Real Devices

### Communication Testing
1. Install on two or more Android devices
2. Grant all requested permissions
3. Disable WiFi on one device to force mesh path
4. Send messages and observe `[mesh]` indicators
5. Re-enable WiFi and observe sync behavior

### AI Assistant Testing
1. **Standard Mode** (default):
   - Send "clock in" - observe ambient confirmation
   - Send "need more wire" - observe material acknowledgment
   - Send "electrical checklist" - observe trade-specific checklist
   - Send "hola" (Spanish) - observe translation response

2. **Hybrid Mode**:
   - Enable in Settings → AI Assistant → Mode: Hybrid
   - Ensure internet connection and battery >20%
   - Test complex queries that exceed rule-based responses
   - Disable internet - observe fallback to Standard mode

3. **Battery Gating**:
   - Lower battery below 20% - observe AI disable warnings
   - Enable power save mode - observe AI restrictions
   - Heat device (if possible) - observe thermal throttling

## Battery Considerations

### Communication
- BLE scan mode: `SCAN_MODE_LOW_POWER`
- BLE advertise mode: `ADVERTISE_MODE_LOW_POWER`
- Scan timeout: 30 seconds idle
- Non-connectable advertising (no connection overhead)

### AI Assistant
- **Standard Mode**: Zero idle battery drain (rule-based only)
- **Hybrid Mode**: Battery gating at 20% threshold
- **Thermal Gating**: Automatic degradation when device temperature >38°C
- **Power Save Mode**: AI disabled when system power saving active
- **Event-Driven**: No background polling, only responds to app events

## Known Limitations (Phase 0)

### Communication
- BLE payload limited to ~24 bytes in advertisement data
- No message chunking for larger payloads
- Chat backend is a logging stub
- No persistent storage (in-memory only)
- Single conversation/channel

### AI Assistant
- Rule-based responses only (no full LLM in Standard mode)
- Limited cached translations (Spanish, French only)
- Trade checklists for major trades only
- No persistent AI context across app restarts

## Next Steps (Phase 1+)

### Communication
- [ ] GATT-based messaging for larger payloads
- [ ] Room database for persistence
- [ ] Real chat backend integration
- [ ] Multi-channel support
- [ ] iOS implementation

### AI Assistant
- [ ] Full on-device LLM integration (Qwen3 model download)
- [ ] Expanded language support (more cached translations)
- [ ] Persistent AI context across app sessions
- [ ] Advanced sub-agents for specialized trade knowledge
- [ ] Voice input/output capabilities
- [ ] Location-aware job site assistance
- [ ] Real-time collaboration features
