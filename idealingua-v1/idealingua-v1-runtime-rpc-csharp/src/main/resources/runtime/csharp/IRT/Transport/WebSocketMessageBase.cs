
using System;
// using Newtonsoft.Json;
// using Newtonsoft.Json.Linq;

namespace IRT.Transport {
    // [JsonConverter(typeof(WebSocketMessageBase_JsonNetConverter))]
    public class WebSocketMessageBase {
        public string Kind;
        public WebSocketMessageBase(string kind = null) {
            Kind = kind;
        }
    }

//    public class WebSocketMessageBase_JsonNetConverter: JsonNetConverter<WebSocketMessageBase> {
//            public override void WriteJson(JsonWriter writer, WebSocketMessageBase holder, JsonSerializer serializer) {
//                throw new Exception("WebSocketMessageBase should never be serialized.");
//            }
//
//            public override WebSocketMessageBase ReadJson(JsonReader reader, System.Type objectType, WebSocketMessageBase existingValue, bool hasExistingValue, JsonSerializer serializer) {
//                var json = JObject.Load(reader);
//                var kind = json["kind"].Value<string>();
//
//                var res = hasExistingValue ? existingValue : new WebSocketMessageBase(kind);
//                res.Kind = kind;
//                return res;
//            }
//        }
}
