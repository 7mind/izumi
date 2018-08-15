
using System;
using IRT.Marshaller;
using IRT;

namespace IRT.Transport {
    public class WebSocketResponseMessage<D> {
        public string Ref;
        public D Data;
        public string Error;
    }
    
    public class WebSocketResponseMessageJson: WebSocketResponseMessage<string> {
    }
}
