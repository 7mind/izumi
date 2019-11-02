
using IRT.Marshaller;

namespace IRT.Transport.Client {
 
    public class WebSocketTransport: WebSocketTransportGeneric<IClientTransportContext> {
        public WebSocketTransport(string endpoint, IJsonMarshaller marshaller, ILogger logger, int timeout = 60) :
            base(endpoint, marshaller, logger, timeout) {
        }
    }
}
