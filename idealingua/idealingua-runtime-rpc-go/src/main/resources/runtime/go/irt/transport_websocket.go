package irt

import (
	"encoding/json"
	"math/rand"
	"time"
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
	MessageRandomLength  = 32

	MessageKindFailure        = "?:failure"
	MessageKindRPCRequest     = "rpc:request"
	MessageKindRPCResponse    = "rpc:response"
	MessageKindRPCFailure     = "rpc:failure"
	MessageKindBuzzerRequest  = "buzzer:request"
	MessageKindBuzzerResponse = "buzzer:request"
	MessageKindBuzzerFailure  = "buzzer:request"
	MessageKindStreamS2C      = "stream:s2c"
	MessageKindStreamC2S      = "stream:c2s"
)

type WebSocketMessageBase struct {
	Kind string `json:"kind"`
}

type WebSocketRequestMessage struct {
	Kind    string            `json:"kind"`
	Service string            `json:"service,omitempty"`
	Method  string            `json:"method,omitempty"`
	ID      string            `json:"id"`
	Data    json.RawMessage   `json:"data,omitempty"`
	Headers map[string]string `json:"headers,omitempty"`
}

type WebSocketResponseMessage struct {
	Kind string          `json:"kind"`
	Ref  string          `json:"ref"`
	Data json.RawMessage `json:"data,omitempty"`
}

type WebSocketFailureMessage struct {
	Kind  string `json:"kind"`
	Cause string `json:"cause"`
	Data  string `json:"data"`
}

func RandomMessageID(prefix string) string {
	b := make([]byte, MessageRandomLength)
	for i := range b {
		b[i] = MessageRandomSymbols[rand.Intn(len(MessageRandomSymbols))]
	}
	return prefix + string(b)
}
