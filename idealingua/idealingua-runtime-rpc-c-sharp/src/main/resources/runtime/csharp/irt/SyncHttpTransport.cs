
using irt;
using System;
using System.IO;
using System.Net;
using System.Text;
using System.Collections.Specialized;

namespace irt {
    public class SyncHttpTransportGeneric<C>: ITransport<C> where C: class {
        private IJsonMarshaller Marshaller;

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

        public SyncHttpTransportGeneric(string endpoint, IJsonMarshaller marshaller, int timeout = 60) {
            Endpoint = endpoint;
            Marshaller = marshaller;
            Timeout = timeout;
        }

        public void Send<I, O>(string service, string method, I payload, TransportCallback<O> callback, C ctx) {
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

                if (payload != null) {
                    var data = Marshaller.Marshal<I>(payload);
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

                            var data = Marshaller.Unmarshal<O>(jsonString);
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

    public class SyncHttpTransport: SyncHttpTransportGeneric<object> {
        public SyncHttpTransport(string endpoint, IJsonMarshaller marshaller, int timeout = 60):
                    base(endpoint, marshaller, timeout) {}
    }
}