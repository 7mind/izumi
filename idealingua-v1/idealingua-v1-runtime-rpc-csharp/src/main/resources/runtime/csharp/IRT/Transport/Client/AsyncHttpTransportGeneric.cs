
using System;
using System.Collections.Generic;
using System.IO;
using System.Net;
using System.Text;
using System.Collections.Specialized;
using IRT.Marshaller;
using IRT.Transport.Authorization;
using IRT.Transport;

namespace IRT.Transport.Client {
    public class AsyncHttpTransportGeneric<C>: IClientTransport<C> where C: class, IClientTransportContext {
        private class RequestState<O> {
            public HttpWebRequest Request;
            public ClientTransportCallback<O> Callback;
            public O Response;
            public string JsonString;

            public RequestState(HttpWebRequest request, ClientTransportCallback<O> callback, string jsonString) {
                Request = request;
                Callback = callback;
                JsonString = jsonString;
            }
        }

        private IJsonMarshaller _marshaller;
        private AuthMethod _auth;
        private Dictionary<string, string> _headers;

        private string _endpoint;
        public string Endpoint {
            get {
                return _endpoint;
            }
            set {
                _endpoint = value;
                if (!_endpoint.EndsWith("\\") && !_endpoint.EndsWith("/")) {
                    _endpoint += "/";
                }
            }
        }

        public int ActiveRequests { get; private set; }
        private int _timeout; // In Seconds

        public AsyncHttpTransportGeneric(string endpoint, IJsonMarshaller marshaller, int timeout = 60) {
            Endpoint = endpoint;
            _marshaller = marshaller;
            _timeout = timeout;
            ActiveRequests = 0;
        }

        public void SetAuthorization(AuthMethod method) {
            _auth = method;
        }

        public AuthMethod GetAuthorization() {
            return _auth;
        }

        public void SetHeaders(Dictionary<string, string> headers) {
            _headers = headers;
        }

        public Dictionary<string, string> GetHeaders() {
            return _headers;
        }

        public virtual void Send<I, O>(string service, string method, I payload, ClientTransportCallback<O> callback, C ctx) {
            try {
                var request = (HttpWebRequest) WebRequest.Create(string.Format("{0}/{1}/{2}", _endpoint, service, method));
                request.Timeout = _timeout * 1000;
                request.Method = payload == null ? "GET" : "POST";
                if (_headers != null) {
                    foreach (var key in _headers.Keys) {
                        request.Headers.Add(key, _headers[key]);
                    }
                }

                if (_auth != null) {
                    request.Headers.Add("Authorization", _auth.ToValue());
                }

                var state = new RequestState<O>(request, callback, null);
                if (payload == null) {
                    request.BeginGetResponse(ProcessResponse<O>, state);
                } else {
                    var data = _marshaller.Marshal<I>(payload);
                    state.JsonString = data;
                    request.ContentType = "application/json";
                    request.BeginGetRequestStream(ProcessStreamRequest<O>, state);
                }
            }
            catch (Exception ex)
            {
                callback.Failure(
                    new TransportException("Unexpected exception occured during async request.", ex)
                );
            }
        }

        protected void ProcessStreamRequest<O>(IAsyncResult asyncResult) {
            RequestState<O> state = (RequestState<O>) asyncResult.AsyncState;
            try {
                HttpWebRequest request = state.Request;
                using (Stream requestStream = request.EndGetRequestStream(asyncResult))
                {
                    using (StreamWriter requestWriter = new StreamWriter(requestStream, Encoding.UTF8))
                        requestWriter.Write(state.JsonString);
                }

                request.BeginGetResponse(ProcessResponse<O>, state);
            }
            catch (Exception ex) {
                state.Callback.Failure(
                    new TransportException("Unexpected exception while streaming request.", ex)
                );
            }
        }

        protected void ProcessResponse<O>(IAsyncResult asyncResult) {
            RequestState<O> state = (RequestState<O>) asyncResult.AsyncState;
            try {
                HttpWebRequest request = state.Request;
                using (HttpWebResponse response = (HttpWebResponse) request.EndGetResponse(asyncResult))
                {
                    using (Stream responseStream = response.GetResponseStream())
                    {
                        using (StreamReader responseReader = new StreamReader(responseStream, Encoding.UTF8))
                        {
                            string jsonString = responseReader.ReadToEnd();
                            if (string.IsNullOrEmpty(jsonString)) {
                                throw new TransportException("Empty Response");
                            }

                            O data;
                            try {
                                data = _marshaller.Unmarshal<O>(jsonString);
                            } catch (Exception ex) {
                                state.Callback.Failure(
                                    new TransportMarshallingException("Unexpected exception occuted while unmarshalling response.", ex)
                                );
                                return;
                            }

                            state.Response = data;
                            state.Callback.Success(state.Response);
                        }
                    }
                }
            }
            catch (Exception ex)
            {
                state.Callback.Failure(
                    new TransportException("Unexpected exception occurred while parsing response.", ex)
                );
            }
        }
    }
}
