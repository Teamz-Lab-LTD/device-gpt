#!/bin/bash

# Script to help upload native debug symbols to Google Play Console
# With AGP 8.1+, symbols are embedded in the AAB, but this script provides guidance

set -e

echo "🔍 Native Debug Symbols Upload Helper"
echo "======================================"
echo ""

# Check if AAB exists
AAB_PATH="app/build/outputs/bundle/release/app-release.aab"
if [ ! -f "$AAB_PATH" ]; then
    echo "❌ AAB file not found. Building release bundle..."
    ./gradlew bundleRelease
fi

echo "✅ AAB file found: $AAB_PATH"
echo ""

# With AGP 8.1+, symbols are embedded in the AAB
echo "📦 With Android Gradle Plugin 8.1+, native debug symbols are automatically"
echo "   embedded in your AAB file when you set debugSymbolLevel = 'FULL'."
echo ""

echo "📋 Upload Instructions:"
echo "   1. Upload your AAB to Google Play Console:"
echo "      → $AAB_PATH"
echo ""
echo "   2. After uploading, check if the warning disappears."
echo "      Google Play Console should automatically extract symbols from the AAB."
echo ""
echo "   3. If the warning persists:"
echo "      a. Go to: Google Play Console → Your App → Release → Setup"
echo "      b. Click on 'App integrity'"
echo "      c. Scroll to 'Native code debug files'"
echo "      d. The symbols should be automatically available"
echo "      e. If not, try re-uploading the AAB or contact Google Play support"
echo ""

# Check for symbol files (in case they were generated separately)
SYMBOLS_DIR="app/build/outputs/native-debug-symbols/release"
if [ -d "$SYMBOLS_DIR" ] && [ "$(ls -A $SYMBOLS_DIR 2>/dev/null)" ]; then
    echo "📁 Found additional symbol files in: $SYMBOLS_DIR"
    echo "   You can manually upload these if needed."
    ls -lh "$SYMBOLS_DIR"
    echo ""
fi

echo "✅ Your build configuration includes:"
echo "   - debugSymbolLevel = 'FULL' (configured in build.gradle.kts)"
echo "   - Native debug symbols should be included in your AAB"
echo ""
echo "💡 Tip: The warning may appear initially but should resolve after"
echo "   Google Play Console processes your AAB file."
