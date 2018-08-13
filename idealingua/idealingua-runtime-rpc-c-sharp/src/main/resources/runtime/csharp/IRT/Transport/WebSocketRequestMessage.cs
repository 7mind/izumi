
using System;
using IRT.Marshaller;
using IRT;

namespace IRT.Transport {
    public class WebSocketRequestMessage<D> {
        public string Service;
        public string Method;
        public string ID;
        public D Data;
        public string Authorization;
    }
    
    public class WebSocketRequestMessageJson: WebSocketRequestMessage<string> {
    }
}
