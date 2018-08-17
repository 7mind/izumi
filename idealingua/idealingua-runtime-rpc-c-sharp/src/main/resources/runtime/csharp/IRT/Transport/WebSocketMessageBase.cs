
using System;

namespace IRT.Transport {
    public class WebSocketMessageBase {
        public string Kind;
        public WebSocketMessageBase(string kind = null) {
            Kind = kind;
        }
    }
}
