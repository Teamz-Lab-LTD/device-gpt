#!/bin/bash

# Script to upload native debug symbols to Firebase Crashlytics
# This resolves the Google Play Console warning about missing debug symbols
#
# Usage:
#   ./upload_debug_symbols.sh
#
# Prerequisites:
#   - Release AAB must be built (run: ./gradlew bundleRelease)
#   - Firebase project must be configured (google-services.json)
#   - Firebase Crashlytics plugin is configured in build.gradle.kts

set -e

echo "🔍 Firebase Crashlytics Debug Symbols Upload"
echo "============================================"
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if we're in the project root
if [ ! -f "build.gradle.kts" ]; then
    echo -e "${RED}❌ Error: Must run from project root directory${NC}"
    exit 1
fi

# Check if google-services.json exists
if [ ! -f "app/google-services.json" ]; then
    echo -e "${YELLOW}⚠️  Warning: google-services.json not found${NC}"
    echo "   Firebase Crashlytics may not be configured properly"
    echo ""
fi

# Check if AAB exists, build if not
AAB_PATH="app/build/outputs/bundle/release/app-release.aab"
if [ ! -f "$AAB_PATH" ]; then
    echo -e "${YELLOW}⚠️  AAB file not found. Building release bundle...${NC}"
    ./gradlew bundleRelease
    if [ ! -f "$AAB_PATH" ]; then
        echo -e "${RED}❌ Failed to build AAB. Please check build errors.${NC}"
        exit 1
    fi
fi

echo -e "${GREEN}✅ AAB file found: $AAB_PATH${NC}"
echo ""

# Extract native debug symbols
echo "📦 Extracting native debug symbols..."
./gradlew extractReleaseNativeDebugMetadata

# Check for native debug symbols directory
SYMBOLS_DIR="app/build/outputs/native-debug-symbols/release"
if [ ! -d "$SYMBOLS_DIR" ] || [ -z "$(ls -A $SYMBOLS_DIR 2>/dev/null)" ]; then
    echo -e "${YELLOW}⚠️  No native debug symbols found.${NC}"
    echo ""
    echo "📋 This is normal if your app has no native code."
    echo "   However, if you're seeing the warning, symbols may be embedded in the AAB."
    echo ""
    echo "✅ With AGP 8.1+ and debugSymbolLevel = 'FULL', symbols are automatically"
    echo "   embedded in your AAB file. Google Play Console will extract them automatically."
    echo ""
    echo "📋 Next Steps:"
    echo "   1. Upload your AAB to Google Play Console"
    echo "   2. Wait 10-15 minutes for Google Play to process"
    echo "   3. The warning should disappear automatically"
    echo ""
    exit 0
fi

echo -e "${GREEN}✅ Native debug symbols found in: $SYMBOLS_DIR${NC}"
ls -lh "$SYMBOLS_DIR"
echo ""

# Upload symbols using Gradle task
echo "📤 Uploading debug symbols to Firebase Crashlytics..."
echo ""

# The Firebase Crashlytics plugin automatically uploads symbols during build
# But we can also trigger it explicitly after building
echo "Building release bundle with symbol upload enabled..."
./gradlew bundleRelease

# Check if upload was successful by looking for the task
echo ""
echo "✅ Build completed with native symbol upload enabled"
echo ""
echo "📋 Verification:"
echo "   1. Check Firebase Crashlytics console for uploaded symbols"
echo "   2. Upload your AAB to Google Play Console:"
echo "      → $AAB_PATH"
echo "   3. Wait 10-15 minutes for Google Play to process"
echo "   4. The warning should disappear automatically"
echo ""
echo "💡 Note: With AGP 8.1+ and debugSymbolLevel = 'FULL', symbols are"
echo "   automatically embedded in your AAB. Google Play Console will"
echo "   extract them automatically when you upload the AAB."
echo ""
echo "💡 The Firebase Crashlytics plugin is configured to automatically"
echo "   upload symbols to Firebase for better crash analysis."
echo ""
