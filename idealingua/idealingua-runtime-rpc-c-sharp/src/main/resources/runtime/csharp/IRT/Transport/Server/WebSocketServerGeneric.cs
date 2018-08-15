
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
        private readonly WebSocketSharp.Server.WebSocketServer wss;
        private readonly Dispatcher<ConnectionContext<C>, string> dispatcher;
        private readonly IJsonMarshaller marshaller;
        private readonly ILogger logger;
        private readonly string endpoint;
        private readonly string path;
        private bool started;

        public bool IsRunning
        {
            get { return started; }
        }

        private class WebSocketServerHandler<C>: WebSocketBehavior {
            private readonly Dispatcher<ConnectionContext<C>, string> dispatcher;
            private readonly IJsonMarshaller marshaller;
            private readonly ILogger logger;
            private ConnectionContext<C> context;
            private readonly IWebSocketServerHandlers<C> handlers;

            public WebSocketServerHandler() {
            }

            public WebSocketServerHandler(Dispatcher<ConnectionContext<C>, string> dispatcher, IJsonMarshaller marshaller,
                ILogger logger, IWebSocketServerHandlers<C> handlers = null) {
                this.handlers = handlers;
                this.dispatcher = dispatcher;
                this.logger = logger;
                this.marshaller = marshaller;
            }

            protected override void OnMessage(MessageEventArgs e) {
                if (e.IsPing) {
                    logger.Logf(LogLevel.Trace, "WebSocketServer: Ping received.");
                    return;
                }

                if (e.IsBinary) {
                    logger.Logf(LogLevel.Error, "WebSocketServer: Binary format messages are not supported.");
                    return;
                }

                logger.Logf(LogLevel.Trace, "WebSocketServer: Incoming message: {0}", e.Data);

                try {
                    var res = new WebSocketResponseMessageJson();
                    var req = marshaller.Unmarshal<WebSocketRequestMessageJson>(e.Data);
                    res.Ref = req.ID;


                    if (req.Authorization != null) {
                        context.System.UpdateAuth(req.Authorization);
                        if (handlers != null) {
                            if (!handlers.OnAuthorize(context)) {
                                Context.WebSocket.Close();
                                return;
                            }
                        }
                        if (string.IsNullOrEmpty(req.Service)) {
                            return;
                        }
                    }

                    try {
                        var data = dispatcher.Dispatch(context, req.Service, req.Method, req.Data);
                        res.Data = data;
                    }
                    catch (Exception ex)
                    {
                        res.Error = ex.Message;
                        res.Data = null;
                    }

                    Send(marshaller.Marshal(res));

                } catch (Exception ex)
                {
                     logger.Logf(LogLevel.Error, "Failure during processing of message. {0}", ex.Message);
                }

            }

            protected override void OnError(ErrorEventArgs e) {
                logger.Logf(LogLevel.Error, "WebSocketServer: Error: {0}. {1}", e.Message, e.Exception.StackTrace);
            }

            protected override void OnOpen() {
                logger.Logf(LogLevel.Trace, "WebSocketServer: Connection opened (Total: {0})", Sessions.Count);
                context = new ConnectionContext<C>();
                context.System = new SystemContext();
                if (handlers != null) {
                    if (!handlers.OnConnect(context, Context)) {
                        Context.WebSocket.Close();
                    }
                }
            }

            protected override void OnClose(CloseEventArgs e) {
                logger.Logf(LogLevel.Trace, "WebSocketServer: Connection closed (Total: {0})", Sessions.Count);
                if (handlers != null) {
                    handlers.OnDisconnect(context);
                }
            }
        }

        public WebSocketServerGeneric(string endpoint, IJsonMarshaller marshaller, ILogger logger,
            IWebSocketServerHandlers<C> handlers = null) {
            this.endpoint = endpoint;
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
            this.endpoint = endpoint.Substring(0, pathStart);
            path = endpoint.Substring(pathStart);
            if (string.IsNullOrEmpty(path)) {
                path = "/";
            }

            logger.Logf(LogLevel.Trace, "WebSocketServer: Endpoint " + this.endpoint);
            logger.Logf(LogLevel.Trace, "WebSocketServer: Path " + path);

            this.marshaller = marshaller;
            this.logger = logger;
            started = false;
            dispatcher = new Dispatcher<ConnectionContext<C>, string>();
            wss = new WebSocketSharp.Server.WebSocketServer(this.endpoint);
            // wss.Log.Level = WebSocketSharp.LogLevel.Debug;
            wss.AddWebSocketService<WebSocketServerHandler<C>>(path,
                () => new WebSocketServerHandler<C>(dispatcher, marshaller, logger, handlers));
        }

        public void AddServiceDispatcher(IServiceDispatcher<ConnectionContext<C>, string> serviceDispatcher) {
            if (started) {
                throw new Exception("Dispatchers should be all added before starting the server.");
            }
            dispatcher.Register(serviceDispatcher);
        }

        public void Start()
        {
            if (started) {
                return;
            }

            logger.Logf(LogLevel.Debug, "Starting WebSocketServer...");
            started = true;
            wss.Start();
        }

        public void Stop()
        {
            wss.Stop();
            logger.Logf(LogLevel.Debug, "Stopped WebSocketServer");
            started = false;
        }
    }
}