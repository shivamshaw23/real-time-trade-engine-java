#!/bin/bash

# Load test runner script
# Usage: ./run-load-test.sh [API_URL]

set -e

API_URL=${1:-http://localhost:8080}
RESULTS_DIR="results"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

echo "Starting load test against $API_URL"
echo "Results will be saved to $RESULTS_DIR/"

# Create results directory
mkdir -p "$RESULTS_DIR"

# Check if k6 is installed
if ! command -v k6 &> /dev/null; then
    echo "Error: k6 is not installed"
    echo "Install from: https://k6.io/docs/getting-started/installation/"
    exit 1
fi

# Run k6 load test
k6 run \
    --env API_URL="$API_URL" \
    --out json="$RESULTS_DIR/result_$TIMESTAMP.json" \
    --out csv="$RESULTS_DIR/result_$TIMESTAMP.csv" \
    k6-script.js

echo ""
echo "Load test completed!"
echo "Results saved to:"
echo "  - $RESULTS_DIR/result_$TIMESTAMP.json"
echo "  - $RESULTS_DIR/result_$TIMESTAMP.csv"

