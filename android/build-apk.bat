@echo off
REM Guild of Smiths - Android APK Build Script with AI Features
REM Version: 0.2.0-ai-alpha

echo ════════════════════════════════════════════════
echo 🔨 GUILD OF SMITHS - Android Build Script
echo Building TradeMesh with Embedded AI Assistant
echo Version: 0.2.0-ai-alpha
echo ════════════════════════════════════════════════

REM Check if we're in the right directory
if not exist "gradlew" (
    echo ❌ Error: gradlew not found. Run from android\ directory.
    pause
    exit /b 1
)

REM Clean previous builds
echo 🧹 Cleaning previous builds...
call gradlew clean

REM Build debug APK
echo 🔨 Building debug APK...
call gradlew assembleDebug

if %ERRORLEVEL% EQU 0 (
    echo ✅ Debug APK built successfully!
    echo 📱 APK location: app\build\outputs\apk\debug\app-debug.apk
) else (
    echo ❌ Debug build failed!
    pause
    exit /b 1
)

REM Optional: Build release APK if keystore is configured
if defined ANDROID_KEYSTORE_PATH (
    echo 🔒 Building release APK...
    call gradlew assembleRelease

    if %ERRORLEVEL% EQU 0 (
        echo ✅ Release APK built successfully!
        echo 📱 APK location: app\build\outputs\apk\release\app-release.apk
    ) else (
        echo ❌ Release build failed!
        pause
        exit /b 1
    )
) else (
    echo ⚠️  Release build skipped (no keystore configured)
    echo    Set ANDROID_KEYSTORE_PATH to enable release builds
)

echo.
echo 🎉 Build complete!
echo AI Features included:
echo   • Ambient AI Assistant (Standard + Hybrid modes)
echo   • Battery-aware operation
echo   • Offline queuing and sync
echo   • Mesh-optimized payloads
echo.
echo 📦 Install with: gradlew installDebug
pause
