
using System;
using System.Collections.Generic;
using System.Timers;
using IRT.Marshaller;
using IRT.Transport.Authorization;
using WebSocketSharp;

namespace IRT.Transport.Client {
    public enum WebSocketTransportState {
        Connecting,
        Connected,
        Stopping,
        Stopped
    }

    public class WebSocketTransportGeneric<C>: IClientTransport<C>, IDisposable where C: class, IClientTransportContext {
        
        public const int DefaultTimeoutInterval = 60;    // Seconds
        protected class WSRequest {
            public System.Timers.Timer Timer;
            public Action<WebSocketResponseMessageJson> Success;
            public Action<Exception> Failure;
        }
        
        private IJsonMarshaller marshaller;
        private WebSocket ws;
        private ILogger logger;
        private bool disposed;
        private bool authorized;
        private AuthMethod auth;
        private string authID;
        private Dictionary<string, WSRequest> requests;

        private WebSocketTransportState state;
        public WebSocketTransportState State {
            get {
                return state;
            }
        }

        public bool Ready {
            get {
                return state == WebSocketTransportState.Connected && authorized;
            }
        }

        private string endpoint;
        public string Endpoint {
            get {
                return endpoint;
            }
            set {
                endpoint = value;
                if (!endpoint.EndsWith("\\") && !endpoint.EndsWith("/")) {
                    endpoint += "/";
                }
            }
        }

        public int Timeout; // In Seconds

        public WebSocketTransportGeneric(string endpoint, IJsonMarshaller marshaller, ILogger logger, int timeout = 60) {
            Endpoint = endpoint;
            this.logger = logger;
            this.marshaller = marshaller;
            Timeout = timeout;
            ws = new WebSocket(endpoint);
            ws.OnMessage += (sender, e) => {
                if (e.IsPing) {
                    logger.Logf(LogLevel.Trace, "WebSocketTransport: Ping received.");
                    return;
                }
                OnMessage(e);
            };
            ws.OnOpen += (sender, e) => {
                OnOpen();
            };
            ws.OnError += (sender, e) => {
                OnError(e);
            };
            ws.OnClose += (sender, e) => {
                OnClose(e);
            };
            ws.Compression = CompressionMethod.Deflate;
            ws.EmitOnPing = true;
            state = WebSocketTransportState.Stopped;
            authorized = true;
            requests = new Dictionary<string, WSRequest>();
        }
        
        private void OnMessage(MessageEventArgs e) {
            logger.Logf(LogLevel.Trace, "WebSocketTransport: Incoming message:\nType: {0}\nData: {1}", e.IsBinary ? "Binary" : "Text", e.Data);

            if (e.IsBinary) {
                throw new Exception("Binary data is not supported.");
            }
            
            HandleSuccess(e.Data);
        }

        private void OnClose(CloseEventArgs e) {
            logger.Logf(LogLevel.Debug, "WebSocketTransport: Closed socket. Code: {0} Reason: {1}", e.Code, e.Reason);
            state = WebSocketTransportState.Stopped;
        }

        private void OnError(WebSocketSharp.ErrorEventArgs e) {
            logger.Logf(LogLevel.Error, "WebSocketTransport: Error: " + e.Message);
            logger.Logf(LogLevel.Debug, "WebSocketTransport: Stacktrace:\n" + e.Exception.StackTrace);
        }

        private void OnOpen() {
            logger.Logf(LogLevel.Debug, "WebSocketTransport: Opened socket.");
            state = WebSocketTransportState.Connected;
        }

        public bool Open() {
            if(state != WebSocketTransportState.Stopped) {
                return false;
            }

            state = WebSocketTransportState.Connecting;
            ws.ConnectAsync();
            return true;
        }

        public void Close() {
            if (state == WebSocketTransportState.Stopped ||
                state != WebSocketTransportState.Stopping) {
                return;
            }

            state = WebSocketTransportState.Stopping;
            ws.Close(CloseStatusCode.Away);
        }

        public void SetAuthorization(AuthMethod method) {
            auth = method;
            Authorize();
        }

        protected string GetRandomMessageId(string prefix = "") {
             var id = Guid.NewGuid();
             return prefix + id;
        }

        protected void HandleFailure(string id, Exception ex) {
            if (!requests.ContainsKey(id)) {
                logger.Logf(LogLevel.Warning, "WebSocketTransport: Can't handle failure, request with ID {0} was not found in the list of requests.", id);
                return;
            }

            var req = requests[id];
            requests.Remove(id);
            if (req.Timer != null) {
                req.Timer.Stop();
                req.Timer = null;   
            }
            
            req.Failure.Invoke(ex);
        }

        protected void HandleSuccess(string data) {
            WebSocketResponseMessageJson res = null;
            try {
                res = marshaller.Unmarshal<WebSocketResponseMessageJson>(data);
            } catch (Exception ex) {
                logger.Logf(LogLevel.Error, "WebSocketTransport: Failed to parse incoming message: {0}. Data: {1}", ex, data);
                return;
            }
            
            if (!requests.ContainsKey(res.Ref)) {
                if (res.Ref == authID) {
                    if (!string.IsNullOrEmpty(res.Error)) {
                        logger.Logf(LogLevel.Error, "WebSocketTransport: Authentication failed {0}", res.Error);
                    }
                    authorized = true;
                    return;
                }
                
                logger.Logf(LogLevel.Warning, "WebSocketTransport: Can't handle success, request with ID {0} was not found in the list of requests.", res.Ref);
                return;
            }

            var req = requests[res.Ref];
            requests.Remove(res.Ref);
            if (req.Timer != null) {
                req.Timer.Stop();
                req.Timer = null;   
            }

            req.Success(res);
        }

        protected void Authorize()
        {
            authorized = false;
            authID = GetRandomMessageId("auth-");
            var msg = new WebSocketRequestMessageJson();
            msg.ID = authID;
            msg.Authorization = auth.ToValue();
            var serialized = marshaller.Marshal(msg);
            logger.Logf(LogLevel.Trace, "WebSocketTransport Authorizing:\n{0}", serialized);
            ws.Send(serialized);
        }

        public virtual void Send<I, O>(string service, string method, I payload, ClientTransportCallback<O> callback, C ctx) {
            try {
                if (!Ready) {
                    throw new Exception("WebSocketTransport is not ready.");
                }
                
                var req = new WebSocketRequestMessageJson();
                req.ID = GetRandomMessageId();
                req.Service = service;
                req.Method = method;
                req.Data = marshaller.Marshal(payload);

                var record = new WSRequest();
                requests.Add(req.ID, record);
                record.Timer = new Timer();
                record.Success = msg => {
                    if (!string.IsNullOrEmpty(msg.Error)) {
                        callback.Failure(new TransportException(msg.Error));
                        return;
                    }

                    try {
                        var data = marshaller.Unmarshal<O>(msg.Data);
                        callback.Success(data);
                    }
                    catch (Exception ex) {
                        callback.Failure(new TransportException("Unexpected exception occured during unmarshalling.", ex));
                    }
                };

                record.Failure = (ex) => {
                    callback.Failure(new TransportException("Request failed. ", ex));
                };
                record.Timer.Interval = DefaultTimeoutInterval * 1000;
                record.Timer.Elapsed += (sender, e) => {
                    HandleFailure(req.ID, new Exception("Request timed out."));
                };

                var serialized = marshaller.Marshal(req);
                logger.Logf(LogLevel.Trace, "WebSocketTransport: Outgoing message: \n{0}", serialized);
                
                ws.SendAsync(serialized, success => {
                    if (success) return;
                    HandleFailure(req.ID, new Exception("Sending request failed."));
                });
                record.Timer.Start();
            }
            catch (Exception ex)
            {
                callback.Failure(
                    new TransportException("Unexpected exception occured during async request.", ex)
                );
            }
        }

        public void Dispose()
        {
            Dispose(true);
            GC.SuppressFinalize(this);
        }

        protected virtual void Dispose(bool disposing)
        {
          if (disposed) {
              return;
          }

          if (disposing) {
             if (ws != null) {
                 Close();
                 ((IDisposable)ws).Dispose();
             }
          }

          disposed = true;
       }
    }
}