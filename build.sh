#!/bin/bash

echo "Building XML Translator Plugin..."

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "❌ Java is not installed. Please install Java 17 or higher."
    exit 1
fi

# Check Java version
java_version=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | awk -F '.' '{print $1}')
if [ "$java_version" -lt 17 ]; then
    echo "❌ Java 17 or higher is required. Current version: $java_version"
    exit 1
fi

echo "✅ Java version: $java_version"

# Make gradlew executable
chmod +x gradlew

# Clean previous builds
echo "🧹 Cleaning previous builds..."
./gradlew clean

# Build the plugin
echo "🔨 Building plugin..."
./gradlew buildPlugin

if [ $? -eq 0 ]; then
    echo "✅ Plugin built successfully!"
    echo "📦 Plugin ZIP file is located at: build/distributions/"
    ls -la build/distributions/
    echo ""
    echo "🚀 To install the plugin:"
    echo "1. Open Android Studio"
    echo "2. Go to File → Settings → Plugins"
    echo "3. Click gear icon → Install Plugin from Disk"
    echo "4. Select the ZIP file from build/distributions/"
    echo "5. Restart Android Studio"
else
    echo "❌ Build failed. Please check the error messages above."
    exit 1
fi