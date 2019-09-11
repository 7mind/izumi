
using System;
using System.IO;
using System.Net;
using System.Threading;
using System.Linq;
using System.Text;
using IRT.Marshaller;

namespace IRT.Transport.Server {
    public class SimpleHttpServerContext {
        public SimpleHttpServerContext(HttpListenerRequest req) {
        }
    }

    public class SimpleHttpServer {
        private readonly HttpListener listener;
        private readonly Dispatcher<SimpleHttpServerContext, string> dispatcher;
        private readonly IJsonMarshaller marshaller;
        private readonly string prefix;
        private bool started;
        private readonly char[] serviceSplitter = new char[] {'/'};

        public SimpleHttpServer(string prefix)
        {
            if (!HttpListener.IsSupported) {
                throw new NotSupportedException( "Needs Windows XP SP2, Server 2003 or later.");
            }

            if (prefix == null) {
                throw new ArgumentException("prefix");
            }

            this.prefix = prefix;
            listener = new HttpListener();
            listener.Prefixes.Add(prefix.EndsWith("/") ? prefix : prefix + "/");

            marshaller = new JsonNetMarshaller();
            dispatcher = new Dispatcher<SimpleHttpServerContext, string>();
        }

        public void AddServiceDispatcher(IServiceDispatcher<SimpleHttpServerContext, string> serviceDispatcher) {
            dispatcher.Register(serviceDispatcher);
        }

        protected string ProcessRequest(HttpListenerRequest req, out string error) {
            var url = req.Url.OriginalString.Substring(prefix.Length, req.Url.OriginalString.Length - prefix.Length);
            var parts = url.Split(serviceSplitter);
            if (parts.Length != 2) {
                error = "protocol://endpoint/service/method is expected, got " + url;
                return null;
            }

            string payload = null;
            if (req.HttpMethod == "POST") {
                var encoding = req.ContentEncoding;
                var reader = new StreamReader(req.InputStream, encoding);
                payload = reader.ReadToEnd();
            }

            var ctx = new SimpleHttpServerContext(req);

            string res;
            try {
                res = dispatcher.Dispatch(ctx, parts[0], parts[1], payload);
            } catch (Exception ex) {
                error = ex.Message + "\n" + ex.StackTrace;
                return null;
            }

            error = null;
            return res;
        }

        public void Start()
        {
            if (started) {
                return;
            }

            started = true;
            listener.Start();
            ThreadPool.QueueUserWorkItem((o) =>
            {
                Console.WriteLine("SimpleHttpServer running...");
                try {
                    while (listener.IsListening) {
                        ThreadPool.QueueUserWorkItem((c) => {
                            var ctx = c as HttpListenerContext;
                            try {
                                string err = null;
                                string res = ProcessRequest(ctx.Request, out err);
                                byte[] buf;
                                if (err != null) {
                                    buf = Encoding.UTF8.GetBytes(err);
                                    ctx.Response.StatusCode = 500;
                                } else {
                                    buf = Encoding.UTF8.GetBytes(res);
                                    ctx.Response.StatusCode = 200;
                                    ctx.Response.ContentType = "application/json";
                                }
                                ctx.Response.ContentLength64 = buf.Length;
                                ctx.Response.OutputStream.Write(buf, 0, buf.Length);
                            }
                            catch { }
                            finally {
                                ctx.Response.OutputStream.Close();
                            }
                        }, listener.GetContext());
                    }
                }
                catch { } // suppress any exceptions
            });
        }

        public void Stop()
        {
            listener.Stop();
            listener.Close();
            started = false;
            Console.WriteLine("SimpleHttpServer stopped.");
        }
    }
}