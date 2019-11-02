
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
    public class SyncHttpTransportGeneric<C>: IClientTransport<C> where C: class, IClientTransportContext {
        private IJsonMarshaller _marshaller;

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

        private int _timeout; // In Seconds
        private AuthMethod _auth;
        private Dictionary<string, string> _headers;

        public SyncHttpTransportGeneric(string endpoint, IJsonMarshaller marshaller, int timeout = 60) {
            _endpoint = endpoint;
            _marshaller = marshaller;
            _timeout = timeout;
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

                if (payload != null) {
                    var data = _marshaller.Marshal<I>(payload);
                    if (data == null) {
                        throw new TransportException("HttpTransport only supports Marshallers which return a string.");
                    }

                    request.Method = "POST";
                    request.ContentType = "application/json";
                    request.ContentLength = data.Length;

                    using (var stream = request.GetRequestStream()) {
                        stream.Write(Encoding.UTF8.GetBytes(data), 0, data.Length);
                    }
                }

                using (var response = (HttpWebResponse)request.GetResponse()) {
                    using (var respStream = response.GetResponseStream()) {
                        using( var reader = new StreamReader (respStream, Encoding.UTF8)) {
                            string jsonString = reader.ReadToEnd();
                            if (string.IsNullOrEmpty(jsonString)) {
                                throw new TransportException("Empty Response");
                            }

                            O data;
                            try {
                                data = _marshaller.Unmarshal<O>(jsonString);
                            } catch (Exception ex) {
                                callback.Failure(
                                    new TransportMarshallingException("Unexpected exception occuted while unmarshalling response.", ex)
                                );
                                return;
                            }

                            callback.Success(data);
                        }
                    }
                }
            }
            catch (Exception ex)
            {
                callback.Failure(
                    new TransportException("Unexpected exception occured during async request.", ex)
                );
            }
        }
    }
}
