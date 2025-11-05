# Load Test Results

This directory contains load test results from k6 runs.

## Results Format

- `result_YYYYMMDD_HHMMSS.json` - k6 JSON output
- `result_YYYYMMDD_HHMMSS.csv` - CSV format for analysis

## Running Load Tests

```bash
cd load-test
./run-load-test.sh http://localhost:8080
```

## Analyzing Results

### k6 HTML Report

```bash
k6 run --out json=results/result.json k6-script.js
k6 report results/result.json
```

### Custom Analysis

Use the CSV files to create custom analysis:
- Import into Excel/Google Sheets
- Use Python pandas for statistical analysis
- Create visualizations (latency over time, throughput, etc.)

## Expected Results

See [docs/PERFORMANCE.md](../docs/PERFORMANCE.md) for expected performance metrics.

