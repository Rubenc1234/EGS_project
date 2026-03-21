#!/bin/bash

# Test the DEV funding endpoint

WALLET="0x86a9906e6bd2ef137d6d5339154611de7a41b178"
AMOUNT=100
ASSET="EUR"

echo "Testing DEV Funding Endpoint"
echo "=========================================="
echo "Wallet: $WALLET"
echo "Amount: $AMOUNT $ASSET"
echo "=========================================="

curl -v -X POST \
  "http://localhost:8081/v1/dev/wallet/$WALLET/fund?amount=$AMOUNT&asset=$ASSET" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer dummy-token"

echo ""
echo "=========================================="
echo "Response above ↑"
echo "=========================================="
