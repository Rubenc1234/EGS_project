package auth

import (
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
)

// GenerateAPIKey creates a secure random API key and its SHA-256 hash.
// We use SHA-256 instead of bcrypt because API keys are checked on every request.
// Bcrypt is intentionally slow and would cripple a high-throughput API.
func GenerateAPIKey() (string, string) {
	bytes := make([]byte, 32) // 256-bit random key
	if _, err := rand.Read(bytes); err != nil {
		panic("crypto/rand failed to read bytes")
	}

	rawKey := fmt.Sprintf("sk_live_%s", hex.EncodeToString(bytes))
	return rawKey, HashAPIKey(rawKey)
}

// HashAPIKey generates a SHA-256 hash for database storage and comparison.
func HashAPIKey(rawKey string) string {
	hash := sha256.Sum256([]byte(rawKey))
	return hex.EncodeToString(hash[:])
}
