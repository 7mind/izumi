
// using irt;
// using System;
// using System.Collections;
// using System.Collections.Generic;
// using Unity.Networking;

// namespace irt.unity {
//     public class UnityTransport: ITransport {
//         private const string CACHE_CONTROL_HEADER_KEY = "Cache-Control";
//         private const string CACHE_CONTROL_HEADER_VALUES = "private, max-age=0, no-cache, no-store";
//         private const string PRAGMA_HEADER_KEY = "Pragma";
//         private const string PRAGMA_HEADER_VALUES = "no-cache";

//         private IJsonMarshaller Marshaller;

//         private string endpoint;
//         public string Endpoint {
//             get {
//                 return endpoint;
//             }
//             set {
//                 endpoint = value;
//                 if (!endpoint.EndsWith("\\") && !endpoint.EndsWith("/")) {
//                     endpoint += "/";
//                 }
//             }
//         }

//         public int ActiveRequests { get; private set; }
//         public int Timeout; // In Seconds
//         public IDictionary<string, string> HttpHeaders;
//         public Action<IEnumerator> CoroutineProcessor;

//         public UnityTransport(string endpoint, IJsonMarshaller marshaller, Action<IEnumerator> coroutineProcessor, int timeout = 60) {
//             Endpoint = endpoint;
//             Marshaller = marshaller;
//             Timeout = timeout;
//             CoroutineProcessor = coroutineProcessor;
//             ActiveRequests = 0;
//         }

//         protected UnityWebRequest PrepareRequest<I>(string service, string method, I payload) {
//             var request = new UnityWebRequest {
//                 downloadHandler = new DownloadHandlerBuffer(),
//                 url = string.Format("{0}/{1}/{2}", endpoint, service, method),
//                 method = input == null ? "GET" : "POST"
//             };

//             if (HttpHeaders != null) {
//                 request.SetHeaders(headers);
//             }

//             // API cached requests might be a pain, let's suppress that
//             request.SetRequestHeader(CACHE_CONTROL_HEADER_KEY, CACHE_CONTROL_HEADER_VALUES);
//             request.SetRequestHeader(PRAGMA_HEADER_KEY, PRAGMA_HEADER_VALUES);

//             if (payload != null)
//             {
//                 var data = Marshaller.Marshal<I>(payload);
//                 if (data == null) {
//                     throw new TransportException("UnityTransport only supports Marshallers which return a string.");
//                 }

//                 byte[] bytes = Encoding.UTF8.GetBytes(data);
//                 request.uploadHandler = new UploadHandlerRaw(bytes) {
//                     contentType = "application/json"
//                 };
//             }

//             request.timeout = Timeout;
//             return request;
//         }

//         protected IEnumerator ProcessRequest<O>(UnityWebRequest req, TransportCallback<O> callback) {
//             ActiveRequests++;
//             yield return req.Send();
//             ActiveRequests--;

//             if (req.isError) {
//                 callback.Failure(new TransportException("Request failed: " + req.error));
//                 yield break;
//             }

//             ProcessResponse(req.downloadHandler.text, req.GetResponseHeaders(), res, callback);
//         }

//         protected void ProcessResponse<O>(string text, Dictionary<string, string> headers, TransportCallback<O> callback) {
//             try {
//                 if (string.IsNullOrEmpty(text)) {
//                     throw new TransportException("Empty response.");
//                 }

//                 var data = Marshaller.Unmarshal<O>(text);
//                 callback.Success(data);
//             } catch (Exception ex) {
//                 callback.Failure(
//                     new TransportException(string.Format("Unexpected exception {0}\n{1}", ex.Message, ex.StackTrace))
//                 );
//             }
//         }

//         public void Send<I, O>(string service, string method, I payload, TransportCallback<O> callback) {
//             if (CoroutineProcessor == null) {
//                 callback.Failure(
//                     new TransportException("UnityTransport requires a coroutine processor to be present before any requests can be executed.")
//                 );
//                 return;
//             }

//             try {
//                 CoroutineProcessor(
//                     ProcessRequest<O>(
//                         PrepareRequest<I>(service, method, payload),
//                         callback)
//                 );
//             } catch (Exception ex) {
//                 callback.Failure(
//                     new TransportException(string.Format("Unexpected exception {0}\n{1}", ex.Message, ex.StackTrace))
//                 );
//             }
//         }
//     }
// }