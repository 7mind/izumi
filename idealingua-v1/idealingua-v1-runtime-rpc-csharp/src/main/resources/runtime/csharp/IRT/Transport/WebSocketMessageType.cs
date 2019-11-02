
using System;

namespace IRT.Transport {
    public static class WebSocketMessageKind {
        public const string Failure = "?:failure";
        public const string RpcRequest = "rpc:request";
        public const string RpcResponse = "rpc:response";
        public const string RpcFailure = "rpc:failure";
        public const string BuzzerRequest = "buzzer:request";
        public const string BuzzerResponse = "buzzer:response";
        public const string BuzzerFailure = "buzzer:failure";
        public const string StreamS2C = "stream:s2c";
        public const string StreamC2S = "stream:c2s";
    }
}
