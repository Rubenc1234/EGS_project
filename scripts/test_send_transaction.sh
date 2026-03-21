#!/bin/bash

# Test script to send a transaction via backend API
# Usage: ./test_send_transaction.sh <from_wallet> <to_wallet> <amount>

FROM_WALLET="${1:-0x86a9906e6bd2ef137d6d5339154611de7a41b178}"
TO_WALLET="${2:-0x49c57ff03fa44d3505b96d439036755a3211ec37}"
AMOUNT="${3:-50}"

echo "Testing POST /v1/transactions"
echo "========================================"
echo "From:  $FROM_WALLET"
echo "To:    $TO_WALLET"
echo "Amount: $AMOUNT EUR"
echo "========================================"

IDEMPOTENCY_KEY="test-$(date +%s)-$RANDOM"

curl -X POST http://localhost:8081/v1/transactions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer dummy-token" \
  -d "{
    \"from_wallet\": \"$FROM_WALLET\",
    \"to_wallet\": \"$TO_WALLET\",
    \"amount\": \"$AMOUNT\",
    \"asset\": \"EUR\",
    \"idempotency_key\": \"$IDEMPOTENCY_KEY\"
  }" \
  -v

echo ""
echo "========================================"
echo "Response above ↑"
echo "========================================"
