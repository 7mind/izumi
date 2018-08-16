
using System.Collections.Generic;

namespace IRT.Transport {
    public class WebSocketRequestMessage<D>: WebSocketMessageBase {
        public string Service;
        public string Method;
        public string ID;
        public D Data;
        public Dictionary<string, string> Headers;

        public WebSocketRequestMessage(string kind): base(kind){
        }
    }
    
    public class WebSocketRequestMessageJson: WebSocketRequestMessage<string> {
        public WebSocketRequestMessageJson(string kind): base(kind){
        }
    }
}
