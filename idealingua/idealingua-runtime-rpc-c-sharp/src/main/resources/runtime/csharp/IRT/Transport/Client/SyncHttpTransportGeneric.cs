
using System;
using System.IO;
using System.Net;
using System.Text;
using System.Collections.Specialized;
using IRT.Marshaller;
using IRT.Transport.Authorization;

namespace IRT.Transport.Client {
    public class SyncHttpTransportGeneric<C>: IClientTransport<C> where C: class, IClientTransportContext {
        private IJsonMarshaller marshaller;

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
        public NameValueCollection HttpHeaders;
        public AuthMethod Auth;

        public SyncHttpTransportGeneric(string endpoint, IJsonMarshaller marshaller, int timeout = 60) {
            Endpoint = endpoint;
            this.marshaller = marshaller;
            Timeout = timeout;
        }

        public void SetAuthorization(AuthMethod method) {
            Auth = method;
        }

        public virtual void Send<I, O>(string service, string method, I payload, ClientTransportCallback<O> callback, C ctx) {
            try {
                var request = (HttpWebRequest) WebRequest.Create(string.Format("{0}/{1}/{2}", endpoint, service, method));
                request.Timeout = Timeout * 1000;
                request.Method = payload == null ? "GET" : "POST";
                if (HttpHeaders != null) {
                    foreach (var key in HttpHeaders.AllKeys) {
                        foreach (var value in HttpHeaders.GetValues(key)) {
                            request.Headers.Add(key, value);
                        }
                    }
                }

                if (Auth != null) {
                    request.Headers.Add("Authorization", Auth.ToValue());
                }

                if (payload != null) {
                    var data = marshaller.Marshal<I>(payload);
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

                            var data = marshaller.Unmarshal<O>(jsonString);
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
