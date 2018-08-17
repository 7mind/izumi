package irt

import (
	"net"
	"net/http"
	"strings"
	"time"
)

const (
	DefaultTimeout = time.Second * 60
)

type TransportHeaders = map[string]string

// Client Specific
type ClientTransport interface {
	GetAuthorization() *Authorization
	SetAuthorization(auth *Authorization) error
	GetHeaders() TransportHeaders
	SetHeaders(headers TransportHeaders) error
	Send(service string, method string, dataIn interface{}, dataOut interface{}) error
}

type ClientSocketTransport interface {
	ClientTransport
	// This one supports Buzzer and Streams
}

// Server specific
type OnConnect func(connection *ConnectionContext, request *http.Request) error
type OnAuth func(connection *ConnectionContext) error
type OnDisconnect func(connection *ConnectionContext)

type ConnectionHandlers struct {
	OnConnect    OnConnect
	OnAuth       OnAuth
	OnDisconnect OnDisconnect
}

type SystemContext struct {
	RemoteAddr string
	Auth       *Authorization
}

func (c *SystemContext) Update(r *http.Request) error {
	header := r.Header
	c.RemoteAddr = header.Get("X-Forwarded-For")
	if c.RemoteAddr != "" {
		// There might be a few IP address, just pick the first one
		ips := strings.Split(c.RemoteAddr, ", ")
		c.RemoteAddr = ips[0]
	} else {
		ip, _, err := net.SplitHostPort(r.RemoteAddr)
		if err == nil {
			c.RemoteAddr = ip
		}
	}

	if c.Auth == nil {
		c.Auth = &Authorization{}
	}

	return c.Auth.UpdateFromHeaders(header)
}

type ConnectionContext struct {
	System *SystemContext
	User   interface{}
}
