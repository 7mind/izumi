
using irt;
using System;
using System.IO;
using System.Net;
using System.Text;
using System.Collections.Specialized;

namespace irt {
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

        public int ActiveRequests { get; private set; }
        public int Timeout; // In Seconds
        public NameValueCollection HttpHeaders;

        public AsyncHttpTransportGeneric(string endpoint, IJsonMarshaller marshaller, int timeout = 60) {
            Endpoint = endpoint;
            Marshaller = marshaller;
            Timeout = timeout;
            ActiveRequests = 0;
        }

        public void Send<I, O>(string service, string method, I payload, ClientTransportCallback<O> callback, C ctx) {
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

                var state = new RequestState<O>(request, callback, null);
                if (payload == null) {
                    request.BeginGetResponse(ProcessResponse<O>, state);
                } else {
                    var data = Marshaller.Marshal<I>(payload);
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

                            var data = Marshaller.Unmarshal<O>(jsonString);
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

    public class AsyncHttpTransport: AsyncHttpTransportGeneric<IClientTransportContext> {
        public AsyncHttpTransport(string endpoint, IJsonMarshaller marshaller, int timeout = 60):
                            base(endpoint, marshaller, timeout) {}
    }
}