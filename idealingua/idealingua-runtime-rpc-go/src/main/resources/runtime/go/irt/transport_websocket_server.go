package irt

import (
	"fmt"
	"net/http"
	"time"

	"github.com/gorilla/websocket"
)

type processorTaskMessage struct {
	connection *wsConnection
	message    *WebSocketRequestMessage
}

type processorResultMessage struct {
	connection *wsConnection
	message    *WebSocketResponseMessage
}

type wsConnection struct {
	conn     *websocket.Conn
	header   http.Header
	messages chan *processorResultMessage
	context  *ConnectionContext
}

type WebSocketServerTransport struct {
	dispatcher *Dispatcher
	marshaller Marshaller
	logger     Logger
	started    bool
	stopping   bool

	handlers *ConnectionHandlers

	upgrader    websocket.Upgrader
	connections map[*wsConnection]bool
	register    chan *wsConnection
	unregister  chan *wsConnection
	terminate   chan bool

	tasks   chan *processorTaskMessage
	results chan *processorResultMessage
}

func NewWebSocketServerTransportEx(dispatcher *Dispatcher, marshaller Marshaller, logger Logger,
	handlers *ConnectionHandlers) *WebSocketServerTransport {
	transport := &WebSocketServerTransport{
		dispatcher: dispatcher,
		upgrader: websocket.Upgrader{
			ReadBufferSize:    DefaultReadBufferSize,
			WriteBufferSize:   DefaultWriteBufferSize,
			EnableCompression: true,
			CheckOrigin: func(r *http.Request) bool {
				return true
			},
		},
		marshaller:  marshaller,
		logger:      logger,
		connections: map[*wsConnection]bool{},
		register:    make(chan *wsConnection, DefaultRegistrationBuffer),
		unregister:  make(chan *wsConnection, DefaultRegistrationBuffer),

		handlers: handlers,

		tasks:   make(chan *processorTaskMessage, DefaultProcessingThreads),
		results: make(chan *processorResultMessage, DefaultProcessingThreads),
	}

	return transport
}

func NewWebSocketServerTransport(dispatcher *Dispatcher, handlers *ConnectionHandlers) *WebSocketServerTransport {
	return NewWebSocketServerTransportEx(dispatcher, NewJSONMarshaller(false), NewConsoleLogger(LogTrace), handlers)
}

func (t *WebSocketServerTransport) runConnectionReader(c *wsConnection) {
	t.logger.Logf(LogTrace, "WebSocketReader - Started")
	defer func() {
		t.unregister <- c
		c.conn.Close()
		t.logger.Logf(LogTrace, "WebSocketReader - Finished")
	}()
	c.conn.SetReadLimit(DefaultMaxPackageSize)
	c.conn.SetReadDeadline(time.Now().Add(DefaultPongWaitTime))
	c.conn.SetPongHandler(func(string) error { c.conn.SetReadDeadline(time.Now().Add(DefaultPongWaitTime)); return nil })
	for {
		msgtype, message, err := c.conn.ReadMessage()
		if err != nil {
			if websocket.IsUnexpectedCloseError(err, websocket.CloseGoingAway, websocket.CloseAbnormalClosure) {
				t.logger.Logf(LogError, "Unexpected connection close: %+v", err.Error())
			}
			break
		}

		if msgtype != websocket.TextMessage {
			t.logger.Logf(LogError, "Unsupported message type! %d", msgtype)
			continue
		}

		msg := &WebSocketRequestMessage{}
		err = t.marshaller.Unmarshal(message, msg)
		if err != nil {
			t.logger.Logf(LogError, "Can't unmarshal %s, body: %s", err.Error(), string(message))
			continue
		}

		if msg.Authorization != "" {
			t.logger.Logf(LogTrace, "Incoming message: Auth Update\n\n%s", msg.Authorization)
			c.context.System.Auth.UpdateFromValue(msg.Authorization)
			if t.handlers != nil && t.handlers.OnAuth != nil {
				if err := t.handlers.OnAuth(c.context); err != nil {
					c.conn.WriteMessage(websocket.CloseMessage, []byte(fmt.Sprintf("{\"error\": \"%s\"}", err.Error())))
					continue
				}
			}

			if msg.Service == "" || msg.Method == "" {
				// This was just auth updated
				c.messages <- &processorResultMessage{
					connection: c,
					message: &WebSocketResponseMessage{
						Ref: msg.ID,
					},
				}
				continue
			}
		}

		var msgType string
		switch msgtype {
		case websocket.TextMessage:
			msgType = "text"
			// case websocket.BinaryMessage:
			// 	msgType = "binary"
			// case websocket.PingMessage: msgType = "ping"
			// case websocket.PongMessage: msgType = "pong"
			// case websocket.CloseMessage: msgType = "close"
		default:
			msgType = "Unknown"
		}

		t.logger.Logf(LogTrace, "Incoming message:\n\nService: %s\nMethod: %s\nID: %s\nType: %s\nData: %s", msg.Service, msg.Method, msg.ID, msgType, string(msg.Data))
		t.tasks <- &processorTaskMessage{
			connection: c,
			message:    msg,
		}
	}
}

func (t *WebSocketServerTransport) runConnectionWriter(c *wsConnection) {
	t.logger.Logf(LogTrace, "WebSocketWriter - Started")
	ticker := time.NewTicker(DefaultPingPeriod)
	defer func() {
		ticker.Stop()
		c.conn.Close()
		t.logger.Logf(LogTrace, "WebSocketWriter - Finished")
	}()
	for {
		select {
		case message, ok := <-c.messages:
			c.conn.SetWriteDeadline(time.Now().Add(DefaultWriteWaitTime))
			if !ok {
				// Channel is closed
				c.conn.WriteMessage(websocket.CloseMessage, []byte{})
				return
			}

			if message.message.Error != "" {
				t.logger.Logf(LogTrace, "Outgoing message:\n\nRef: %s\nError: %s", message.message.Ref, message.message.Error)
			} else {
				t.logger.Logf(LogTrace, "Outgoing message:\n\nRef: %s\nData: %s", message.message.Ref, string(message.message.Data))
			}

			data, err := t.marshaller.Marshal(message.message)
			if err != nil {
				t.logger.Logf(LogError, "Can't marshal %s", err.Error())
				continue
			}

			w, err := c.conn.NextWriter(websocket.TextMessage)
			if err != nil {
				return
			}
			w.Write(data)

			if err := w.Close(); err != nil {
				return
			}
		case <-ticker.C:
			t.logger.Logf(LogTrace, "WebSocket - Ping")
			c.conn.SetWriteDeadline(time.Now().Add(DefaultWriteWaitTime))
			if err := c.conn.WriteMessage(websocket.PingMessage, nil); err != nil {
				return
			}
		}
	}
}

func (t *WebSocketServerTransport) runProcessor() {
	t.logger.Logf(LogTrace, "WebSocket Requests Processor - Started")
	for {
		select {
		case <-t.terminate:
			t.logger.Logf(LogTrace, "WebSocket Requests Processor - Finished")
			return

		case task := <-t.tasks:
			{
				msg := &WebSocketResponseMessage{
					Ref: task.message.ID,
				}
				result := &processorResultMessage{
					connection: task.connection,
					message:    msg,
				}

				data, err := t.dispatcher.Dispatch(task.connection.context, task.message.Service, task.message.Method, task.message.Data)
				if err != nil {
					msg.Error = err.Error()
					t.results <- result
					continue
				}

				msg.Data = data
				t.results <- result
			}
		}
	}
}

func (t *WebSocketServerTransport) run() {

	t.terminate = make(chan bool)
	go func() {
		t.logger.Logf(LogDebug, "WebSocketTransport - Started")
		for {
			select {
			case <-t.terminate:
				t.logger.Logf(LogDebug, "Termination requested, closing connections...")
				for c := range t.connections {
					c.conn.Close()
				}
				t.stopping = false
				t.started = false
				t.logger.Logf(LogDebug, "WebSocketTransport - Finished")
				return
			case connection := <-t.register:
				t.connections[connection] = true
				go t.runConnectionReader(connection)
				go t.runConnectionWriter(connection)
				t.logger.Logf(LogDebug, "Connected (Connections %d)", len(t.connections))
			case connection := <-t.unregister:
				if _, ok := t.connections[connection]; ok {
					close(connection.messages)
					delete(t.connections, connection)
				}
				if t.handlers != nil && t.handlers.OnDisconnect != nil {
					t.handlers.OnDisconnect(connection.context)
				}

				t.logger.Logf(LogDebug, "Disconnected (Connections %d)", len(t.connections))
			case result := <-t.results:
				select {
				case result.connection.messages <- result:
				default:
					close(result.connection.messages)
					_, ok := t.connections[result.connection]
					if ok {
						delete(t.connections, result.connection)
					}
				}
			}
		}
	}()

	for i := 0; i < DefaultProcessingThreads; i++ {
		go t.runProcessor()
	}
}

func (t *WebSocketServerTransport) Start() {
	if t.started {
		return
	}

	t.started = true
	t.run()
}

func (t *WebSocketServerTransport) Stop() {
	if !t.started || t.stopping {
		return
	}

	t.stopping = true
	close(t.terminate)
}

func (t *WebSocketServerTransport) ServeWS(w http.ResponseWriter, r *http.Request) {
	if !t.started {
		t.logger.Logf(LogError, "WebSocket Transport wasn't started and can't server requests.")
		return
	}

	// if r.URL.Scheme != "ws" && r.URL.Scheme != "wss" {
	// 	t.logger.Logf(LogError, "WebSocket Transport expects the scheme to be ws:// or wss://, got %s. rejecting.", r.URL.Scheme)
	// 	return
	// }

	conn, err := t.upgrader.Upgrade(w, r, nil)
	if err != nil {
		t.logger.Logf(LogWarning, "failed to upgrade connection: %s", err.Error())
		return
	}

	wsConnection := &wsConnection{
		conn:     conn,
		messages: make(chan *processorResultMessage, DefaultConnectionBuffer),
		context: &ConnectionContext{
			System: &SystemContext{
				Auth:       &Authorization{},
				RemoteAddr: r.RemoteAddr,
			},
			User: nil,
		},
	}

	err = wsConnection.context.System.Update(r)
	if err != nil {
		t.logger.Logf(LogWarning, "failed to properly update system context: %s", err.Error())
	}

	if t.handlers != nil && t.handlers.OnConnect != nil {
		if err := t.handlers.OnConnect(wsConnection.context, r); err != nil {
			wsConnection.conn.WriteMessage(websocket.CloseMessage, []byte(fmt.Sprintf("{\"error\": \"%s\"}", err.Error())))
			return
		}
	}

	t.register <- wsConnection
}
