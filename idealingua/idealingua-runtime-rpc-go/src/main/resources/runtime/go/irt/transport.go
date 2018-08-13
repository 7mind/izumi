package irt

import (
    "net/http"
    "strings"
    "net"
    "time"
)

const (
    DefaultTimeout = time.Second * 60
)

// Client Specific

type ClientTransport interface {
    SetAuthorization(auth *Authorization) error
    Send(service string, method string, dataIn interface{}, dataOut interface{}) error
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
    User interface{}
}
