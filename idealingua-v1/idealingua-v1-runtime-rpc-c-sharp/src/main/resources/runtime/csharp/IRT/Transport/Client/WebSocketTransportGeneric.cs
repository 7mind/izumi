
using System;
using System.Collections.Generic;
using System.Timers;
using IRT.Marshaller;
using IRT.Transport.Authorization;
using WebSocketSharp;
using IRT.Transport;

namespace IRT.Transport.Client {
    public enum WebSocketTransportState {
        Connecting,
        Connected,
        Stopping,
        Stopped
    }

    public class WebSocketTransportGeneric<C>: IClientSocketTransport<C, string>, IDisposable where C: class, IClientTransportContext {

        public const int DefaultTimeoutInterval = 60;    // Seconds
        protected class WSRequest {
            public System.Timers.Timer Timer;
            public Action<WebSocketResponseMessageJson> Success;
            public Action<Exception> Failure;
        }

        private IJsonMarshaller _marshaller;
        private WebSocket _ws;
        private ILogger _logger;
        private bool _disposed;
        private bool _headersUpdated;
        private AuthMethod _auth;
        private string _headersUpdateID;
        private Dictionary<string, WSRequest> _requests;
        private Dictionary<string, string> _headers;
        private Dispatcher<C, string> _dispatcher;
        private C _context;

        private WebSocketTransportState _state;
        public WebSocketTransportState State {
            get {
                return _state;
            }
        }

        public bool Ready {
            get {
                return _state == WebSocketTransportState.Connected && _headersUpdated;
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

        private int _timeout; // In Seconds

        public WebSocketTransportGeneric(string endpoint, IJsonMarshaller marshaller, ILogger logger, int timeout = 60) {
            Endpoint = endpoint;
            _logger = logger;
            _marshaller = marshaller;
            _timeout = timeout;
            _ws = new WebSocket(endpoint);
            _ws.OnMessage += (sender, e) => {
                if (e.IsPing) {
                    logger.Logf(LogLevel.Trace, "WebSocketTransport: Ping received.");
                    return;
                }
                OnMessage(e);
            };
            _ws.OnOpen += (sender, e) => {
                OnOpen();
            };
            _ws.OnError += (sender, e) => {
                OnError(e);
            };
            _ws.OnClose += (sender, e) => {
                OnClose(e);
            };
            _ws.Compression = CompressionMethod.Deflate;
            _ws.EmitOnPing = true;
            _state = WebSocketTransportState.Stopped;
            _headersUpdated = true;
            _requests = new Dictionary<string, WSRequest>();
            _dispatcher = new Dispatcher<C, string>();
        }

        private void OnMessage(MessageEventArgs e) {
            _logger.Logf(LogLevel.Trace, "WebSocketTransport: Incoming message:\nType: {0}\nData: {1}", e.IsBinary ? "Binary" : "Text", e.Data);

            if (e.IsBinary) {
                throw new Exception("Binary data is not supported.");
            }

            WebSocketMessageBase messageBase;
            try {
                messageBase = _marshaller.Unmarshal<WebSocketMessageBase>(e.Data);
            }
            catch (Exception ex) {
                _logger.Logf(LogLevel.Error, "Exception during message unmarshalling: {0}", ex.Message);
                return;
            }

            switch (messageBase.Kind) {
                case WebSocketMessageKind.RpcFailure:
                case WebSocketMessageKind.RpcResponse: {
                    WebSocketResponseMessageJson msg;
                    try {
                        msg = _marshaller.Unmarshal<WebSocketResponseMessageJson>(e.Data);
                    }
                    catch (Exception ex) {
                        _logger.Logf(LogLevel.Error, "Exception during RPC message unmarshalling: {0}", ex.Message);
                        return;
                    }
                    HandleRPCSuccess(msg);
                } break;

                case WebSocketMessageKind.BuzzerRequest: {
                    WebSocketRequestMessageJson msg;
                    try {
                        msg = _marshaller.Unmarshal<WebSocketRequestMessageJson>(e.Data);
                    }
                    catch (Exception ex) {
                        _logger.Logf(LogLevel.Error, "Exception during RPC message unmarshalling: {0}", ex.Message);
                        return;
                    }
                    HandleBuzzer(msg);
                } break;

                case WebSocketMessageKind.Failure: {
                    WebSocketFailureMessage msg;
                    try {
                        msg = _marshaller.Unmarshal<WebSocketFailureMessage>(e.Data);
                    } catch (Exception ex) {
                        _logger.Logf(LogLevel.Error, "Exception during failure message unmarshalling: {0}", ex.Message);
                        return;
                    }
                    HandleFailure(msg);
                } break;
               default:
                   throw new Exception("Not implemented");
            }
        }

        private void OnClose(CloseEventArgs e) {
            _logger.Logf(LogLevel.Debug, "WebSocketTransport: Closed socket. Code: {0} Reason: {1}", e.Code, e.Reason);
            _state = WebSocketTransportState.Stopped;
        }

        private void OnError(WebSocketSharp.ErrorEventArgs e) {
            _logger.Logf(LogLevel.Error, "WebSocketTransport: Error: " + e.Message);
            _logger.Logf(LogLevel.Debug, "WebSocketTransport: Stacktrace:\n" + e.Exception.StackTrace);
        }

        private void OnOpen() {
            _logger.Logf(LogLevel.Debug, "WebSocketTransport: Opened socket.");
            _state = WebSocketTransportState.Connected;
        }

        public bool Open() {
            if(_state != WebSocketTransportState.Stopped) {
                return false;
            }

            _state = WebSocketTransportState.Connecting;
            _ws.ConnectAsync();
            return true;
        }

        public void Close() {
            if (_state == WebSocketTransportState.Stopped ||
                _state != WebSocketTransportState.Stopping) {
                return;
            }

            _state = WebSocketTransportState.Stopping;
            _ws.Close(CloseStatusCode.Away);
        }

        public void SetAuthorization(AuthMethod method) {
            _auth = method;
            sendHeaders();
        }

        public AuthMethod GetAuthorization() {
            return _auth;
        }

        public void SetHeaders(Dictionary<string, string> headers) {
            _headers = headers;
            sendHeaders();
        }

        public Dictionary<string, string> GetHeaders() {
            return _headers;
        }

        protected string GetRandomMessageId(string prefix = "") {
             var id = Guid.NewGuid();
             return prefix + id;
        }

        protected void HandleRPCFailure(string id, Exception ex) {
            if (!_requests.ContainsKey(id)) {
                _logger.Logf(LogLevel.Warning, "Can't handle failure, request with ID {0} was not found in the list of requests.", id);
                return;
            }

            var req = _requests[id];
            _requests.Remove(id);
            if (req.Timer != null) {
                req.Timer.Stop();
                req.Timer = null;
            }

            req.Failure.Invoke(ex);
        }

        protected void HandleRPCSuccess(WebSocketResponseMessageJson msg) {
            if (!_requests.ContainsKey(msg.Ref)) {
                if (msg.Ref == _headersUpdateID) {
                    if (msg.Kind == WebSocketMessageKind.RpcFailure) {
                        _logger.Logf(LogLevel.Error, "Headers update failed {0}", msg.Data);
                    } else {
                        _logger.Logf(LogLevel.Debug, "Headers updated succesfully.");
                    }
                    _headersUpdated = true;
                    return;
                }

                _logger.Logf(LogLevel.Warning, "Can't handle RPC response, request with ID {0} was not found in the list of requests.", msg.Ref);
                return;
            }

            var req = _requests[msg.Ref];
            _requests.Remove(msg.Ref);
            if (req.Timer != null) {
                req.Timer.Stop();
                req.Timer = null;
            }

            if (msg.Kind == WebSocketMessageKind.RpcFailure) {
                req.Failure.Invoke(new Exception(msg.Data));
                return;
            }

            req.Success.Invoke(msg);
        }

        protected void HandleBuzzer(WebSocketRequestMessageJson msg)
        {
            var res = new WebSocketResponseMessageJson(WebSocketMessageKind.BuzzerResponse);
            res.Ref = msg.ID;
            try
            {
                res.Data = _dispatcher.Dispatch(_context, msg.Service, msg.Method, msg.Data);
            } catch (Exception ex)
            {
                res.Kind = WebSocketMessageKind.BuzzerFailure;
                res.Data = ex.Message;
            }

            _ws.Send(_marshaller.Marshal(res));
        }

        protected void HandleFailure(WebSocketFailureMessage msg) {
            _logger.Logf(LogLevel.Error, "WebSocketTransport: Failure message received from the server:\nCause: {0}\nData:\n{1}", msg.Cause, msg.Data);
        }

        protected void sendHeaders()
        {
            _headersUpdated = false;
            _headersUpdateID = GetRandomMessageId("headersupdate-");
            var msg = new WebSocketRequestMessageJson(WebSocketMessageKind.RpcRequest);
            msg.ID = _headersUpdateID;
            msg.Headers = new Dictionary<string, string>();
            if (_headers != null) {
                foreach (var key in _headers.Keys) {
                    msg.Headers.Add(key, _headers[key]);
                }
            }

            if (_auth != null) {
                msg.Headers.Add("Authorization", _auth.ToValue());
            }

            var serialized = _marshaller.Marshal(msg);
            _logger.Logf(LogLevel.Trace, "WebSocketTransport Updating headers:\n{0}", serialized);
            _ws.Send(serialized);
        }

        public virtual void Send<I, O>(string service, string method, I payload, ClientTransportCallback<O> callback, C ctx) {
            try {
                if (!Ready) {
                    throw new Exception("WebSocketTransport is not ready.");
                }

                var req = new WebSocketRequestMessageJson(WebSocketMessageKind.RpcRequest);
                req.ID = GetRandomMessageId();
                req.Service = service;
                req.Method = method;
                req.Data = _marshaller.Marshal(payload);

                var record = new WSRequest();
                _requests.Add(req.ID, record);
                record.Timer = new Timer();
                record.Success = msg => {
                    O data;
                    try {
                        data =_marshaller.Unmarshal<O>(msg.Data);
                    }
                    catch (Exception ex) {
                        callback.Failure(new TransportMarshallingException("Unexpected exception occured during unmarshalling.", ex));
                        return;
                    }
                    callback.Success(data);
                };

                record.Failure = (ex) => {
                    callback.Failure(new TransportException("Request failed. ", ex));
                };
                record.Timer.Interval = DefaultTimeoutInterval * 1000;
                record.Timer.Elapsed += (sender, e) => {
                    HandleRPCFailure(req.ID, new Exception("Request timed out."));
                };

                var serialized = _marshaller.Marshal(req);
                _logger.Logf(LogLevel.Trace, "WebSocketTransport: Outgoing message: \n{0}", serialized);

                _ws.SendAsync(serialized, success => {
                    if (success) return;
                    HandleRPCFailure(req.ID, new Exception("Sending request failed."));
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
          if (_disposed) {
              return;
          }

          if (disposing) {
             if (_ws != null) {
                 Close();
                 ((IDisposable)_ws).Dispose();
             }
          }

          _disposed = true;
       }

        public bool RegisterBuzzer(IServiceDispatcher<C, string> buzzer) {
            return _dispatcher.Register(buzzer);
        }

        public bool UnregisterBuzzer(string id) {
            return _dispatcher.Unregister(id);
        }

        public void SetBuzzerContext(C context) {
            _context = context;
        }

        public C GetBuzzerContext() {
            return _context;
        }
    }
}