package cache

import (
	"context"
	"log/slog"
	"os"

	"github.com/redis/go-redis/v9"
)

func InitRedis(addr string) *redis.Client {
	rdb := redis.NewClient(&redis.Options{
		Addr:     addr,
		Password: "", // no password set
		DB:       0,  // use default DB
	})

	if err := rdb.Ping(context.Background()).Err(); err != nil {
		slog.Error("Failed to connect to Redis", "error", err)
		os.Exit(1)
	}

	slog.Info("Connected to Redis successfully.")
	return rdb
}
