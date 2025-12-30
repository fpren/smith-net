# ble-mesh-multiplatform

## Overview
The `ble-mesh-multiplatform` project is a comprehensive trade communication and productivity platform featuring:

- **Dual-path communication**: BLE mesh for offline, real-time messaging + IP chat for persistent storage
- **Embedded AI Assistant**: Ambient, contextual help without explicit commands (Standard/Hybrid modes)
- **Cross-platform support**: Android, iOS, desktop with unified experience
- **Trade-focused features**: Time tracking, job management, material logging, geofencing

The project implements Guild of Smiths – a complete field service management solution for construction and trade workers.

## Directory Structure
- **android/**: Android app with embedded AI assistant, BLE mesh, and dual-path communication
- **backend/**: Node.js/Express API server with AI integration and Supabase storage
- **desktop/python/ble_mesh**: Core BLE mesh networking implementation
- **desktop/python/tests**: Unit tests for BLE mesh package
- **desktop/portal**: Web-based management portal
- **ios**: iOS implementation (placeholder)
- **supabase/**: Database migrations and setup scripts
- **components/**: Shared components (job board, time tracking)
- **shared/protocol**: Cross-platform protocol definitions

## Quick Start

### Prerequisites
- Node.js 18+ (for backend)
- Python 3.8+ (for BLE mesh)
- Android Studio (for mobile development)
- Supabase account

### Backend Setup
```bash
cd backend
npm install
cp .env.example .env  # Configure your Supabase credentials
npm run dev
```

### Android Setup
```bash
cd android
# Configure Supabase credentials in app/src/main/java/com/guildofsmiths/trademesh/data/SupabaseClient.kt
# Build and install on device
./gradlew installDebug
```

### Database Setup
- Create Supabase project
- Run migrations from `supabase/migrations/`
- Configure storage buckets

## Key Features

### 🤖 Embedded AI Assistant
- **Standard Mode**: Local rule-based AI, zero battery drain, always available
- **Hybrid Mode**: Local + cloud AI when connected and charged
- **Ambient Operation**: No explicit commands needed - observes contextually
- **Mesh-Optimized**: Compressed payloads for BLE transport

### 📡 Dual-Path Communication
- **Mesh Path (BLE)**: Offline, real-time, short messages
- **Chat Path (IP)**: Persistent, ordered, longer content
- **Auto-sync**: Seamless failover between paths

### 🛠️ Trade Tools
- Time tracking with geofence validation
- Job management with material logging
- Photo documentation with AI analysis
- Invoice generation with audit trails

## Architecture Overview

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Android App   │    │   Backend API   │    │   Supabase DB   │
│                 │    │                 │    │                 │
│ • AI Assistant  │◄──►│ • LLM Interface │◄──►│ • Messages      │
│ • BLE Mesh      │    │ • WebSocket     │    │ • Jobs          │
│ • Dual Path     │    │ • Media Upload  │    │ • Time Entries  │
│ • Geofencing    │    │                 │    │ • Audit Logs    │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         └───────────────────────┼───────────────────────┘
                                 ▼
                    ┌─────────────────┐
                    │   BLE Mesh      │
                    │   (Python)      │
                    └─────────────────┘
```

## Contributing

### Development Areas
- **AI/ML**: Enhance rule-based responses, add new LLM providers
- **Mobile**: iOS implementation, cross-platform improvements
- **Backend**: API optimization, real-time performance
- **BLE Mesh**: Protocol improvements, multi-device support

### Getting Started
1. Review `android/README.md` for mobile development
2. Check `SUPABASE_SETUP.md` for backend configuration
3. See `android/README.md` for AI testing procedures

## License
This project is licensed under the MIT License. See the LICENSE file for more details.