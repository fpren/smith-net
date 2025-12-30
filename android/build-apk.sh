#!/bin/bash

# Guild of Smiths - Android APK Build Script with AI Features
# Version: 0.2.0-ai-alpha

echo "🔨 GUILD OF SMITHS - Android Build Script"
echo "Building TradeMesh with Embedded AI Assistant"
echo "Version: 0.2.0-ai-alpha"
echo "═══════════════════════════════════════════════"

# Check if we're in the right directory
if [ ! -f "gradlew" ]; then
    echo "❌ Error: gradlew not found. Run from android/ directory."
    exit 1
fi

# Clean previous builds
echo "🧹 Cleaning previous builds..."
./gradlew clean

# Build debug APK
echo "🔨 Building debug APK..."
./gradlew assembleDebug

if [ $? -eq 0 ]; then
    echo "✅ Debug APK built successfully!"
    echo "📱 APK location: app/build/outputs/apk/debug/app-debug.apk"
else
    echo "❌ Debug build failed!"
    exit 1
fi

# Optional: Build release APK if keystore is configured
if [ -n "$ANDROID_KEYSTORE_PATH" ]; then
    echo "🔒 Building release APK..."
    ./gradlew assembleRelease

    if [ $? -eq 0 ]; then
        echo "✅ Release APK built successfully!"
        echo "📱 APK location: app/build/outputs/apk/release/app-release.apk"
    else
        echo "❌ Release build failed!"
        exit 1
    fi
else
    echo "⚠️  Release build skipped (no keystore configured)"
    echo "   Set ANDROID_KEYSTORE_PATH to enable release builds"
fi

echo ""
echo "🎉 Build complete!"
echo "AI Features included:"
echo "  • Ambient AI Assistant (Standard + Hybrid modes)"
echo "  • Battery-aware operation"
echo "  • Offline queuing and sync"
echo "  • Mesh-optimized payloads"
echo ""
echo "📦 Install with: ./gradlew installDebug"
