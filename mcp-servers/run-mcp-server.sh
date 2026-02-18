#!/bin/bash

# Build the project
echo "Building Git MCP Server..."
./gradlew clean build -x test

# Check if build was successful
if [ $? -eq 0 ]; then
    echo "Build successful!"
    echo "Starting Git MCP Server..."
    java -jar build/libs/git-0.0.1-SNAPSHOT.jar
else
    echo "Build failed!"
    exit 1
fi
