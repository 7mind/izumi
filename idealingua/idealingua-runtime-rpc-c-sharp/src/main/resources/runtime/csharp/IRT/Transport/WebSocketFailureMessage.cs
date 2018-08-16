
namespace IRT.Transport {
    public class WebSocketFailureMessage: WebSocketMessageBase {
        public string Cause;
        public string Data;

        public WebSocketFailureMessage(): base(WebSocketMessageKind.Failure){
        }
    }
}
