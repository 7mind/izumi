
using System;
using System.IO;
using System.Net;
using System.Text;
using System.Collections.Specialized;
using IRT.Marshaller;

namespace IRT.Transport.Client {
    public class AsyncHttpTransport: AsyncHttpTransportGeneric<IClientTransportContext> {
        public AsyncHttpTransport(string endpoint, IJsonMarshaller marshaller, int timeout = 60):
                            base(endpoint, marshaller, timeout) {}
    }
}