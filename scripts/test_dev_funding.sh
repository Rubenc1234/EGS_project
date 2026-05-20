#!/bin/bash

# Test the DEV funding endpoint

WALLET="0xf22b7e23782b4f7ec38e4c4148e1171a5eb5fc4e"
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
