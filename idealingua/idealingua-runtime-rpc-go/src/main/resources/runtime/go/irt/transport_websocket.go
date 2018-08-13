package irt

import (
	"encoding/json"
	"time"
	"math/rand"
)

const (
	DefaultWriteWaitTime      = 10 * time.Second
	DefaultPongWaitTime       = 60 * time.Second
	DefaultPingPeriod         = (DefaultPongWaitTime * 9) / 10
	DefaultMaxPackageSize     = 1024 * 1024 * 16 // 16 MB
	DefaultReadBufferSize     = 1024 * 8         // 8 KB
	DefaultWriteBufferSize    = 1024 * 8         // 8 KB
	DefaultProcessingThreads  = 2
	DefaultRegistrationBuffer = 2
	DefaultConnectionBuffer   = 5

	MessageRandomSymbols = "abcdefghijklmnopqrstuvwxyz0123456789"
	MessageRandomLength = 32
)

type WebSocketRequestMessage struct {
	Service        string          `json:"service,omitempty"`
	Method         string          `json:"method,omitempty"`
	ID             string          `json:"id"`
	Data           json.RawMessage `json:"data,omitempty"`
	Authorization  string          `json:"authorization,omitempty"`
}

type WebSocketResponseMessage struct {
	Ref   string          `json:"ref"`
	Data  json.RawMessage `json:"data,omitempty"`
	Error string          `json:"error,omitempty"`
}

func RandomMessageID(prefix string) string {
	b := make([]byte, MessageRandomLength)
	for i := range b {
		b[i] = MessageRandomSymbols[rand.Intn(len(MessageRandomSymbols))]
	}
	return prefix + string(b)
}