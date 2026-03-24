package auth

import (
	"errors"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

// GenerateSubscriberToken creates a short-lived token for frontend browsers.
// It embeds the ClientID to guarantee strict tenant isolation.
func GenerateSubscriberToken(secret string, clientID uint, userID string, expMinutes int) (string, error) {
	token := jwt.NewWithClaims(jwt.SigningMethodHS256, jwt.MapClaims{
		"role":     "subscriber",
		"clientID": clientID,
		"userID":   userID,
		"exp":      time.Now().Add(time.Duration(expMinutes) * time.Minute).Unix(),
		"iat":      time.Now().Unix(),
	})

	return token.SignedString([]byte(secret))
}

func ValidateAndExtract(tokenString, secret string) (jwt.MapClaims, error) {
	token, err := jwt.Parse(tokenString, func(token *jwt.Token) (interface{}, error) {
		if _, ok := token.Method.(*jwt.SigningMethodHMAC); !ok {
			return nil, errors.New("unexpected signing method")
		}
		return []byte(secret), nil
	})

	if err != nil {
		return nil, err
	}

	if claims, ok := token.Claims.(jwt.MapClaims); ok && token.Valid {
		return claims, nil
	}

	return nil, errors.New("invalid token")
}
