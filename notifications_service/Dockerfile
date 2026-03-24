# Build Stage
FROM golang:1.26-alpine AS builder

WORKDIR /app

# Install dependencies for Swagger
RUN apk add --no-cache git
RUN go install github.com/swaggo/swag/cmd/swag@latest

# Copy modules
COPY go.mod go.sum ./
RUN go mod download

# Copy source code
COPY . .

# Generate Swagger Documentation
RUN swag init -g cmd/api/main.go

# Build the Go binary
RUN CGO_ENABLED=0 GOOS=linux go build -o egs-notifications cmd/api/main.go

# Final Stage
FROM alpine:latest

WORKDIR /app

# Install ca-certificates for Web Push external HTTPS requests
RUN apk --no-cache add ca-certificates tzdata

COPY --from=builder /app/egs-notifications .
COPY --from=builder /app/docs ./docs
COPY --from=builder /app/webagent ./webagent

EXPOSE 8080

CMD ["./egs-notifications"]
