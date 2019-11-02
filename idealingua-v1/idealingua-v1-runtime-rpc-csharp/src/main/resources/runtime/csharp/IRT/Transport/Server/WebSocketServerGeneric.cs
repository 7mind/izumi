
using System;
using System.IO;
using System.Net;
using System.Threading;
using System.Linq;
using System.Net.Mime;
using System.Text;
using IRT.Marshaller;
using WebSocketSharp;
using WebSocketSharp.Net.WebSockets;
using WebSocketSharp.Server;
using ErrorEventArgs = WebSocketSharp.ErrorEventArgs;

namespace IRT.Transport.Server {
    public interface IWebSocketServerHandlers<C> {
        bool OnConnect(ConnectionContext<C> context, WebSocketContext request);
        bool OnAuthorize(ConnectionContext<C> context);
        void OnDisconnect(ConnectionContext<C> context);
    }

    public class WebSocketServerGeneric<C> {
        private readonly WebSocketSharp.Server.WebSocketServer _wss;
        private readonly Dispatcher<ConnectionContext<C>, string> _dispatcher;
        private readonly IJsonMarshaller _marshaller;
        private readonly ILogger _logger;
        private readonly string _endpoint;
        private readonly string _path;
        private bool _started;

        public bool IsRunning
        {
            get { return _started; }
        }

        private class WebSocketServerHandler<CH>: WebSocketBehavior {
            private readonly Dispatcher<ConnectionContext<CH>, string> _dispatcher;
            private readonly IJsonMarshaller _marshaller;
            private readonly ILogger _logger;
            private ConnectionContext<CH> _context;
            private readonly IWebSocketServerHandlers<CH> _handlers;

            public WebSocketServerHandler() {
            }

            public WebSocketServerHandler(Dispatcher<ConnectionContext<CH>, string> dispatcher, IJsonMarshaller marshaller,
                ILogger logger, IWebSocketServerHandlers<CH> handlers = null) {
                _handlers = handlers;
                _dispatcher = dispatcher;
                _logger = logger;
                _marshaller = marshaller;
            }

            protected void sendFailure(string cause, string data) {
                var failure = new WebSocketFailureMessage();
                failure.Cause = cause;
                failure.Data = data;
                Send(_marshaller.Marshal(failure));
            }

            protected override void OnMessage(MessageEventArgs e) {
                if (e.IsPing) {
                    _logger.Logf(LogLevel.Trace, "WebSocketServer: Ping received.");
                    return;
                }

                if (e.IsBinary) {
                    _logger.Logf(LogLevel.Error, "WebSocketServer: Binary format messages are not supported.");
                    return;
                }

                _logger.Logf(LogLevel.Trace, "WebSocketServer: Incoming message: {0}", e.Data);

                WebSocketMessageBase messageBase;
                try {
                    messageBase = _marshaller.Unmarshal<WebSocketMessageBase>(e.Data);
                } catch (Exception ex)
                {
                    _logger.Logf(LogLevel.Error, "Failure during processing of base message. {0}", ex.Message);
                    sendFailure(ex.Message, e.Data);
                    return;
                }

                switch (messageBase.Kind) {
                    case WebSocketMessageKind.RpcRequest:
                    {
                        var res = new WebSocketResponseMessageJson(WebSocketMessageKind.RpcResponse);
                        WebSocketRequestMessageJson req;
                        try {
                            req = _marshaller.Unmarshal<WebSocketRequestMessageJson>(e.Data);
                        } catch (Exception ex)
                        {
                            _logger.Logf(LogLevel.Error, "Failure during processing of RPC request message. {0}", ex.Message);
                            sendFailure(ex.Message, e.Data);
                            return;
                        }

                        res.Ref = req.ID;

                        if (req.Headers != null && req.Headers.ContainsKey("Authorization")) {
                            _context.System.UpdateAuth(req.Headers["Authorization"]);
                            if (_handlers != null) {
                                if (!_handlers.OnAuthorize(_context)) {
                                    Context.WebSocket.Close();
                                    return;
                                }
                            }
                            if (string.IsNullOrEmpty(req.Service)) {
                                Send(_marshaller.Marshal(res));
                                return;
                            }
                        }

                        try {
                            var data = _dispatcher.Dispatch(_context, req.Service, req.Method, req.Data);
                            res.Data = data;
                        }
                        catch (Exception ex)
                        {
                            res.Kind = WebSocketMessageKind.RpcFailure;
                            res.Data = ex.Message;
                        }

                        Send(_marshaller.Marshal(res));
                    } break;
                    default: {
                        sendFailure("Unsupported request.", e.Data);
                    } break;
                }
            }

            protected override void OnError(ErrorEventArgs e) {
                _logger.Logf(LogLevel.Error, "WebSocketServer: Error: {0}. {1}", e.Message, e.Exception.StackTrace);
            }

            protected override void OnOpen() {
                _logger.Logf(LogLevel.Trace, "WebSocketServer: Connection opened (Total: {0})", Sessions.Count);
                _context = new ConnectionContext<CH>();
                _context.System = new SystemContext();
                if (_handlers != null) {
                    if (!_handlers.OnConnect(_context, Context)) {
                        Context.WebSocket.Close();
                    }
                }
            }

            protected override void OnClose(CloseEventArgs e) {
                _logger.Logf(LogLevel.Trace, "WebSocketServer: Connection closed (Total: {0})", Sessions.Count);
                if (_handlers != null) {
                    _handlers.OnDisconnect(_context);
                }
            }
        }

        public WebSocketServerGeneric(string endpoint, IJsonMarshaller marshaller, ILogger logger,
            IWebSocketServerHandlers<C> handlers = null) {
            this._endpoint = endpoint;
            bool secured;

            if (endpoint.StartsWith("ws://")) {
                secured = false;
            } else
            if (endpoint.StartsWith("wss://")) {
                secured = true;
            } else {
                throw new Exception("Endpoint for a websocket server must start with ws:// or ws://");
            }

            var pathStart = endpoint.IndexOf('/', secured ? 6 : 5);
            _endpoint = endpoint.Substring(0, pathStart);
            _path = endpoint.Substring(pathStart);
            if (string.IsNullOrEmpty(_path)) {
                _path = "/";
            }

            logger.Logf(LogLevel.Trace, "WebSocketServer: Endpoint " + _endpoint);
            logger.Logf(LogLevel.Trace, "WebSocketServer: Path " + _path);

            _marshaller = marshaller;
            _logger = logger;
            _started = false;
            _dispatcher = new Dispatcher<ConnectionContext<C>, string>();
            _wss = new WebSocketSharp.Server.WebSocketServer(_endpoint);
            // wss.Log.Level = WebSocketSharp.LogLevel.Debug;
            _wss.AddWebSocketService<WebSocketServerHandler<C>>(_path,
                () => new WebSocketServerHandler<C>(_dispatcher, marshaller, logger, handlers));
        }

        public void AddServiceDispatcher(IServiceDispatcher<ConnectionContext<C>, string> serviceDispatcher) {
            if (_started) {
                throw new Exception("Dispatchers should be all added before starting the server.");
            }
            _dispatcher.Register(serviceDispatcher);
        }

        public void Start()
        {
            if (_started) {
                return;
            }

            _logger.Logf(LogLevel.Debug, "Starting WebSocketServer...");
            _started = true;
            _wss.Start();
        }

        public void Stop()
        {
            _wss.Stop();
            _logger.Logf(LogLevel.Debug, "Stopped WebSocketServer");
            _started = false;
        }
    }
}