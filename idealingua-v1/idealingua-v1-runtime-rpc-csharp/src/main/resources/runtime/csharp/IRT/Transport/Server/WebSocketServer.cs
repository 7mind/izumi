
using System;
using System.IO;
using System.Net;
using System.Threading;
using System.Linq;
using System.Net.Mime;
using System.Text;
using IRT.Marshaller;
using WebSocketSharp;
using WebSocketSharp.Net.WebSockets;
using WebSocketSharp.Server;
using ErrorEventArgs = WebSocketSharp.ErrorEventArgs;

namespace IRT.Transport.Server {
    public class WebSocketServer: WebSocketServerGeneric<object> {
        public WebSocketServer(string endpoint, IJsonMarshaller marshaller, ILogger logger,
            IWebSocketServerHandlers<object> handlers = null): base(endpoint, marshaller, logger, handlers) {
        }
    }
}