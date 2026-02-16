#!/bin/bash

# Clean up any stray lockfile in parent directory
rm -f package-lock.json 2>/dev/null

cd admin-ui

# Clear .next cache if --clean flag is passed
if [ "$1" = "--clean" ]; then
  echo "Clearing .next cache..."
  rm -rf .next
fi

# Check if node_modules exists, if not run npm install
if [ ! -d "node_modules" ]; then
  echo "node_modules not found. Running npm install..."
  npm install
fi

npm run dev
