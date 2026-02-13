cd admin-ui

# Check if node_modules exists, if not run npm install
if [ ! -d "node_modules" ]; then
  echo "node_modules not found. Running npm install..."
  npm install
fi

npm run dev