using System;
using System.Text;
using System.Collections;
using System.Collections.Generic;
using UnityEngine.Networking;
using IRT.Marshaller;
using IRT.Transport.Client;
using IRT.Transport.Authorization;

#if UNITY_EDITOR
using UnityEditor;
#endif

namespace IRT.Transport.Client.Unity {
    public class UnityTransportGeneric<C>: IClientTransport<C> where C: class, IClientTransportContext {
        private const string CACHE_CONTROL_HEADER_KEY = "Cache-Control";
        private const string CACHE_CONTROL_HEADER_VALUES = "private, max-age=0, no-cache, no-store";
        private const string PRAGMA_HEADER_KEY = "Pragma";
        private const string PRAGMA_HEADER_VALUES = "no-cache";
        private IJsonMarshaller Marshaller;
        private string endpoint;
        public string Endpoint         {
            get { return endpoint; }
            set {
                endpoint = value;
                if (!endpoint.EndsWith("\\") && !endpoint.EndsWith("/"))
                {
                    endpoint += "/";
                }
            }
        }

        public int ActiveRequests { get; private set; }
        public int Timeout; // In Seconds
        public IDictionary<string, string> HttpHeaders;
        public AuthMethod Auth;

        public void SetAuthorization(AuthMethod method) {
            Auth = method;
        }

#if !UNITY_EDITOR
        public Action<IEnumerator> CoroutineProcessor;
#endif

#if !UNITY_EDITOR
        public UnityTransportGeneric(string endpoint, IJsonMarshaller marshaller,
                Action<IEnumerator> coroutineProcessor,
                int timeout = 60) {
            Endpoint = endpoint;
            Marshaller = marshaller;
            Timeout = timeout;
            CoroutineProcessor = coroutineProcessor;
            ActiveRequests = 0;
        }
#else
        public UnityTransportGeneric(string endpoint, IJsonMarshaller marshaller, int timeout = 60) {
            Endpoint = endpoint;
            Marshaller = marshaller;
            Timeout = timeout;
            ActiveRequests = 0;
        }
#endif

        protected UnityWebRequest PrepareRequest<I>(string service, string method, I payload) {
            var request = new UnityWebRequest {
                downloadHandler = new DownloadHandlerBuffer(),
                url = string.Format("{0}/{1}/{2}", endpoint, service, method),
                method = payload == null ? "GET" : "POST"
            };

            if (HttpHeaders != null) {
                foreach (KeyValuePair<string, string> kv in HttpHeaders) {
                    request.SetRequestHeader(kv.Key, kv.Value);
                }
            }

            if (Auth != null) {
                request.SetRequestHeader("Authorization", Auth.ToValue());
            }

            // API cached requests might be a pain, let's suppress that
            request.SetRequestHeader(CACHE_CONTROL_HEADER_KEY, CACHE_CONTROL_HEADER_VALUES);
            request.SetRequestHeader(PRAGMA_HEADER_KEY, PRAGMA_HEADER_VALUES);
            if (payload != null) {
                var data = Marshaller.Marshal<I>(payload);
                if (data == null) {
                    throw new TransportException("UnityTransport only supports Marshallers which return a string.");
                }

                byte[] bytes = Encoding.UTF8.GetBytes(data);
                request.uploadHandler = new UploadHandlerRaw(bytes) {
                    contentType = "application/json"
                };
            }
            request.timeout = Timeout;
            return request;
        }

        public void Send<I, O>(string service, string method, I payload, ClientTransportCallback<O> callback, C ctx) {
#if !UNITY_EDITOR
            if (CoroutineProcessor == null) {
                callback.Failure(
                    new TransportException(
                        "UnityTransport requires a coroutine processor to be present before any requests can be executed.")
                );
                return;
            }
#endif
            try {
#if !UNITY_EDITOR
                CoroutineProcessor(ProcessRequest(PrepareRequest(service, method, payload), callback));
#else
                ProcessRequest(PrepareRequest(service, method, payload), callback);
#endif
            }
            catch (Exception ex) {
                callback.Failure(
                    new TransportException(string.Format("Unexpected exception {0}\n{1}", ex.Message, ex.StackTrace))
                );
            }
        }
#if !UNITY_EDITOR
        protected IEnumerator ProcessRequest<O>(UnityWebRequest req, ClientTransportCallback<O> callback) {
            ActiveRequests++;
            yield return req.Send();
            ActiveRequests--;

            if (req.isError) {
                callback.Failure(new TransportException("Request failed: " + req.error));
                yield break;
            }
            ProcessResponse(req.downloadHandler.text, req.GetResponseHeaders(), callback);
        }

        #else
        protected void ProcessRequest<T>(UnityWebRequest request, ClientTransportCallback<T> callback) {
            Wait(() => !request.isDone, () => {
                if (request.isError) {
                    callback.Failure(new TransportException("Request failed: " + request.error));
                    return;
                }
                ProcessResponse(request.downloadHandler.text, request.GetResponseHeaders(), callback);
            });
            request.Send();
        }
#endif
        protected void ProcessResponse<O>(string text, Dictionary<string, string> headers,
                ClientTransportCallback<O> callback) {

            try {
                if (string.IsNullOrEmpty(text)) {
                    throw new TransportException("Empty response.");
                }
                var data = Marshaller.Unmarshal<O>(text);
                callback.Success(data);
            } catch (Exception ex) {
                callback.Failure(
                    new TransportException(string.Format("Unexpected exception {0}\n{1}", ex.Message, ex.StackTrace))
                );
            }
        }

#if UNITY_EDITOR
        private class EditorHandler {
            public EditorApplication.CallbackFunction Callback;
        }

        private static void Wait(Func<bool> waitUntil, Action callback) {
            EditorHandler editorHandler = null;
            editorHandler = new EditorHandler {
                Callback = () => {
                    if (waitUntil()) {
                        return;
                    }
                    EditorApplication.update -= editorHandler.Callback;
                    callback();
                }
            };
            EditorApplication.update += editorHandler.Callback;
        }
#endif
    }
    public class UnityTransport : UnityTransportGeneric<IClientTransportContext> {
#if !UNITY_EDITOR
        public UnityTransport(string endpoint, IJsonMarshaller marshaller, Action<IEnumerator> coroutineProcessor,
            int timeout = 60) : base(endpoint, marshaller, coroutineProcessor, timeout) {
        }
#else
        public UnityTransport(string endpoint, IJsonMarshaller marshaller, int timeout = 60) : base(endpoint,
            marshaller, timeout) {
        }
#endif
    }
}