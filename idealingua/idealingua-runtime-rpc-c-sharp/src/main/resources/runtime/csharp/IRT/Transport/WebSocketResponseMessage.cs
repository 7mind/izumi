
namespace IRT.Transport {
    public class WebSocketResponseMessage<D>: WebSocketMessageBase {
        public string Ref;
        public D Data;

        public WebSocketResponseMessage(string kind): base(kind){
        }
    }
    
    public class WebSocketResponseMessageJson: WebSocketResponseMessage<string> {
        public WebSocketResponseMessageJson(string kind): base(kind){
        }
    }
}
