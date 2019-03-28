package irt

import (
	"crypto/tls"
	"fmt"
	"time"

	"github.com/gorilla/websocket"
)

type wsClientTransport struct {
	// Implements ClientTransport
	endpoint   string
	dialer     *websocket.Dialer
	conn       *websocket.Conn
	logger     Logger
	marshaller Marshaller
	headers    TransportHeaders
	auth       *Authorization
}

func NewWebSocketClientTransportEx(endpoint string, timeout time.Duration, skipSSLVerify bool, subprotocols []string,
	enableCompression bool, marshaller Marshaller, logger Logger) (ClientTransport, error) {
	client := &wsClientTransport{
		logger:     logger,
		marshaller: marshaller,
		endpoint:   endpoint,
		headers:    map[string]string{},
		dialer: &websocket.Dialer{
			TLSClientConfig:   &tls.Config{InsecureSkipVerify: skipSSLVerify},
			Subprotocols:      subprotocols,
			HandshakeTimeout:  timeout,
			EnableCompression: enableCompression,
			ReadBufferSize:    DefaultReadBufferSize,
			WriteBufferSize:   DefaultWriteBufferSize,
		},
	}
	conn, _, err := client.dialer.Dial(client.endpoint, nil)
	if err != nil {
		return nil, err
	}

	client.conn = conn
	return client, nil
}

func NewWebSocketClientTransport(endpoint string, subprotocols []string) (ClientTransport, error) {
	return NewWebSocketClientTransportEx(endpoint, DefaultTimeout, false, subprotocols, true,
		NewJSONMarshaller(false), NewDummyLogger())
}

func (t *wsClientTransport) requestResponse(req *WebSocketRequestMessage) (*WebSocketResponseMessage, error) {
	reqData, err := t.marshaller.Marshal(req)
	if err != nil {
		t.logger.Logf(LogError, "Request message marshalling failed: %s", err.Error())
		return nil, err
	}

	t.logger.Logf(LogTrace, "Request message:\n`%s`", string(reqData))

	err = t.conn.WriteMessage(websocket.TextMessage, reqData)
	if err != nil {
		t.logger.Logf(LogError, "Request message sending failed: %s", err.Error())
		return nil, err
	}

	t.conn.ReadMessage()

	messageType, respData, err := t.conn.ReadMessage()
	if err != nil {
		t.logger.Logf(LogError, "Response message reading failed: %s", err.Error())
		return nil, err
	}

	if messageType != websocket.TextMessage {
		t.logger.Logf(LogError, "Response message type is not text. Got %+v", messageType)
		return nil, err
	}

	t.logger.Logf(LogTrace, "Response message:\n`%s`", string(respData))

	res := &WebSocketResponseMessage{}
	err = t.marshaller.Unmarshal(respData, res)
	if err != nil {
		t.logger.Logf(LogError, "Response message unmarshalling failed: %s", err.Error())
		return nil, err
	}

	if res.Ref != req.ID {
		err := fmt.Errorf("response message reference doesn't match the request ID. Expected: %s got %s",
			req.ID, res.Ref)
		t.logger.Logf(LogError, err.Error())
		return nil, err
	}

	return res, nil
}

func (t *wsClientTransport) GetHeaders() TransportHeaders {
	return t.headers
}

func (t *wsClientTransport) SetHeaders(headers TransportHeaders) error {
	t.headers = headers
	return t.sendHeaders()
}

func (t *wsClientTransport) GetAuthorization() *Authorization {
	return t.auth
}

func (t *wsClientTransport) SetAuthorization(auth *Authorization) error {
	t.auth = auth
	return t.sendHeaders()
}

func (t *wsClientTransport) sendHeaders() error {
	req := &WebSocketRequestMessage{}
	req.ID = RandomMessageID("updateheaders-")
	req.Headers = map[string]string{}
	if t.auth == nil {
		req.Headers["Authorization"] = ""
	} else {
		req.Headers["Authorization"] = t.auth.ToValue()
	}

	for k, v := range t.headers {
		req.Headers[k] = v
	}

	t.logger.Logf(LogDebug, "================================================")
	t.logger.Logf(LogDebug, "Updating Authorization: %+v", req.Headers)

	_, err := t.requestResponse(req)
	if err != nil {
		return err
	}

	t.logger.Logf(LogDebug, "================================================")
	return nil
}

func (t *wsClientTransport) Send(service string, method string, dataIn interface{}, dataOut interface{}) error {
	t.logger.Logf(LogDebug, "================================================")
	t.logger.Logf(LogDebug, "Requesting Service: %s, Method: %s", service, method)
	var data []byte
	var err error
	if dataIn != nil {
		data, err = t.marshaller.Marshal(dataIn)
		if err != nil {
			t.logger.Logf(LogError, "Data in marshalling failed: %s", err.Error())
			return err
		}
	}

	req := &WebSocketRequestMessage{}
	req.Service = service
	req.Method = method
	req.ID = RandomMessageID("")
	req.Data = data

	res, err := t.requestResponse(req)
	if err != nil {
		return err
	}

	if dataOut != nil {
		if err = t.marshaller.Unmarshal(res.Data, dataOut); err != nil {
			msg := fmt.Sprintf("error while unmarshalling response message %+v. Data: %s.", err, string(res.Data))
			t.logger.Logf(LogError, msg)
			return fmt.Errorf(msg)
		}
	}

	t.logger.Logf(LogDebug, "================================================")
	return nil
}
