# BLE Mesh Multi-Platform: Copilot Instructions

## Project Overview

**BLE Mesh Multi-Platform** is a cross-platform Bluetooth Low Energy (BLE) mesh networking system for sending text messages and images across iOS, Android, and Desktop (Linux/macOS/Windows). The project follows a **phased approach**: Python desktop prototype (Phase 1) → iOS (Phase 2) → Android (Phase 3).

## Architecture & Design Decisions

### Why Multi-Phase with Desktop First?
- **Phase 1 (Desktop/Python)**: Fastest iteration, protocol validation, cross-platform testing on laptops
- **Phase 2-3 (iOS/Android)**: Native implementations after protocol is proven stable
- **Rationale**: Prototyping complex BLE protocol in Python with bleak is 10x faster than native mobile development

### BLE Protocol Strategy

**Not using Bluetooth Mesh SIG specification** (too complex). Instead: **Simplified Mesh using BLE GATT** with star topology expanding to multi-hop:

1. **Packet Structure** (max 520 bytes to fit BLE MTU):
   - **Header** (16 bytes): Message ID, Type (0x01=text, 0x02=image), Fragment Index, Total Fragments, Flags
   - **Payload** (max 500 bytes): Actual data
   - **Footer** (4 bytes): CRC32 checksum

2. **Text Flow**: User input → Packet wrap → Send via BLE characteristic → Receiver notifies → ACK

3. **Image Flow**: Compress (JPEG, quality 85%) → Fragment into 500-byte chunks → Send sequentially with delays → Receiver assembles → Verify CRC → Retry failed fragments

### Service UUID
```
Primary: 0000FF00-0000-1000-8000-00805F9B34FB
├── Text Characteristic:     0000FF01-...
├── Image Fragment Char:     0000FF02-...
├── Control Characteristic:  0000FF03-...
└── Status Characteristic:   0000FF04-...
```

## Project Structure & Key Files

```
desktop/python/
├── ble_mesh/
│   ├── __init__.py          (package init)
│   ├── scanner.py           (device discovery - TODO)
│   ├── peripheral.py        (advertise mode - TODO)
│   ├── protocol.py          (packet handling - TODO)
│   ├── fragmenter.py        (fragment/reassemble - TODO)
│   └── compressor.py        (image compression - TODO)
├── tests/
│   └── __init__.py          (test package)
├── main.py                  (CLI entry point - TODO)
└── requirements.txt         (dependencies)

shared/
├── protocol/                (shared spec documentation)
└── docs/                    (project documentation)

.claude/
├── commands/                (reusable Claude prompts)
└── Claude.md               (detailed project config - from setup_script.sh)

.github/
└── copilot-instructions.md (this file)
```

## Development Workflows

### Environment Setup
```bash
# One-time setup
cd desktop/python
python3 -m venv venv

# Activate (platform-specific)
source venv/bin/activate        # Linux/macOS
venv\Scripts\activate           # Windows PowerShell

# Install dependencies
pip install --upgrade pip
pip install -r requirements.txt
```

### Running & Testing
```bash
# Scan for BLE devices
python main.py scan

# Run tests
pytest tests/

# Run specific test file
pytest tests/test_protocol.py -v
```

### Key Dependencies (in requirements.txt)
- **bleak** (≥0.21.0): Cross-platform BLE library
- **Pillow** (≥10.0.0): Image compression/processing
- **protobuf** (≥4.24.0): Message serialization (optional, consider for protocol)
- **crc** (≥6.0.0): CRC32 checksums
- **pytest** (≥7.4.0), **pytest-asyncio** (≥0.21.0): Testing
- **click** (≥8.1.0), **rich** (≥13.0.0): CLI interface & formatting

## Code Patterns & Conventions

### Async/Await Pattern (Critical!)
All BLE operations are **async** using bleak. Every module must use `asyncio`:

```python
import asyncio
from bleak import BleakScanner

async def scan_devices(duration=10):
    """Always async for BLE operations"""
    devices = await BleakScanner.discover(timeout=duration)
    return devices

# Call with asyncio.run()
if __name__ == "__main__":
    asyncio.run(scan_devices())
```

### Module Organization
- **scanner.py**: Device discovery, scanning
- **peripheral.py**: Advertise BLE service, expose characteristics
- **protocol.py**: Packet struct encode/decode, message serialization
- **fragmenter.py**: Split large data into 500-byte chunks, reassemble fragments
- **compressor.py**: Image JPEG compression (quality 85%), thumbnail generation

Each module should have clear responsibilities with minimal cross-module coupling.

### Testing Pattern
- Use `pytest` + `pytest-asyncio`
- Test async functions with `@pytest.mark.asyncio` decorator
- Isolate external dependencies (mock BLE device interactions)
- Example location: `tests/test_protocol.py`, `tests/test_fragmenter.py`

### Error Handling
- BLE connections can drop unexpectedly → implement retry logic with exponential backoff
- Packet loss is normal → rely on CRC validation and ACK mechanism
- Image corruption → verify CRC before assembly, log failed fragments

## Integration Points

### Cross-Platform Protocol
The **packet format and fragmentation logic** must remain consistent across Python (Phase 1), Swift/iOS (Phase 2), and Kotlin/Android (Phase 3). Store protocol specifications in `shared/protocol/` as canonical reference.

### CLI Interface
Main entry point in `desktop/python/main.py` uses `click` for commands and `rich` for formatted output:
```bash
python main.py scan              # Discover devices
python main.py send-text <msg>   # Send text message
python main.py send-image <file> # Send image
```

### Testing Strategy (Priorities)
1. **Unit Tests**: Packet serialization, fragmentation, CRC validation
2. **Integration Tests**: BLE scanner finds devices, connection flow
3. **Real Device Tests**: Two laptops send/receive, 3+ devices relay, mixed platforms

## Common Tasks & Patterns

### Adding a New BLE Module
1. Create file in `desktop/python/ble_mesh/`
2. Use async/await throughout
3. Add corresponding tests in `tests/`
4. Update `requirements.txt` if new dependencies needed

### Implementing a Feature
1. Write unit tests first (TDD approach)
2. Implement in Python, validate with real BLE hardware
3. Document integration points for iOS/Android ports
4. Update `shared/protocol/PROTOCOL.md` if changing packet format

### Debugging BLE Issues
- Enable verbose logging: `import logging; logging.basicConfig(level=logging.DEBUG)`
- Verify MTU negotiation (typical ~512 bytes)
- Check characteristic read/write/notify permissions
- Test with `bleak` CLI: `python -m bleak` for scanning

## Success Metrics & Verification

- [ ] Text message delivery < 1 second
- [ ] 100 KB image transfer < 30 seconds
- [ ] 95%+ delivery success rate with retry
- [ ] Works across Linux, macOS, Windows
- [ ] Graceful degradation on BLE connection loss

## Resources & References

- **Bleak Docs**: https://bleak.readthedocs.io/
- **BLE MTU & Throughput**: https://punchthrough.com/maximizing-ble-throughput-part-2-mtu-gatt/
- **BLE GATT Protocol**: https://learn.adafruit.com/introduction-to-bluetooth-low-energy/gatt
- **Protocol Spec**: `shared/protocol/PROTOCOL.md` (will be created during Phase 1)

## Phase Roadmap

**Phase 1 (Desktop/Python)** - Weeks 1-4:
- Week 1-2: Scanner + peripheral mode
- Week 3: Text message protocol
- Week 4: Image fragmentation & compression

**Phase 2 (iOS)** - After Phase 1:
- Port protocol to Swift + CoreBluetooth
- Create SwiftUI UI
- Test iOS ↔ Desktop

**Phase 3 (Android)** - After Phase 2:
- Port protocol to Kotlin + Android BLE API
- Create Jetpack Compose UI
- Test Android ↔ iOS ↔ Desktop

## Next Actions for AI Agent

1. **Understand the Protocol First**: Read the packet structure and fragmentation strategy above
2. **Follow Async Patterns**: All BLE work uses `asyncio` and `async/await`
3. **Test-First Approach**: Write tests before implementation
4. **Check Dependencies**: Verify all imports from `requirements.txt` before suggesting features
5. **Document Packet Changes**: Any protocol modifications must update `shared/protocol/` specs
