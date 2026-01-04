#!/bin/bash

# Smith Net - APK Release Update Script
# Run this from the project root to build and update APKs

echo "🔨 Building Smith Net APKs..."

# Build debug APK
cd android
./gradlew assembleDebug

if [ $? -eq 0 ]; then
    echo "✅ Debug APK built successfully"
    cp app/build/outputs/apk/debug/app-debug.apk ../releases/apks/smith-net-debug.apk
    echo "📦 Copied debug APK to releases/apks/"
else
    echo "❌ Debug build failed"
    exit 1
fi

# Build release APK if keystore is available
if [ -n "$ANDROID_KEYSTORE_PATH" ]; then
    ./gradlew assembleRelease
    if [ $? -eq 0 ]; then
        echo "✅ Release APK built successfully"
        cp app/build/outputs/apk/release/app-release.apk ../releases/apks/smith-net-release.apk
        echo "📦 Copied release APK to releases/apks/"
    fi
else
    echo "⚠️  Release build skipped (no keystore configured)"
fi

cd ..

echo ""
echo "🎉 APK update complete!"
echo "APKs available in: releases/apks/"
echo ""
echo "To commit these changes:"
echo "git add releases/"
echo "git commit -m 'Update APK releases'"
echo "git push"