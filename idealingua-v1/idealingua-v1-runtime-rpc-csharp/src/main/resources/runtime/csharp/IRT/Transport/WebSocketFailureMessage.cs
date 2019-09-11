// using Newtonsoft.Json;
// using Newtonsoft.Json.Linq;

namespace IRT.Transport {
    // [JsonConverter(typeof(WebSocketFailureMessage_JsonNetConverter))]
    public class WebSocketFailureMessage: WebSocketMessageBase {
        public string Cause;
        public string Data;

        public WebSocketFailureMessage(): base(WebSocketMessageKind.Failure){
        }
    }

//    public class WebSocketFailureMessage_JsonNetConverter : JsonNetConverter<WebSocketFailureMessage>
//        {
//            public override void WriteJson(JsonWriter writer, WebSocketFailureMessage holder, JsonSerializer serializer)
//            {
//                writer.WriteStartObject();
//                // Kind
//                writer.WritePropertyName("kind");
//                writer.WriteValue(holder.Kind);
//
//                // Cause
//                writer.WritePropertyName("cause");
//                writer.WriteValue(holder.Cause);
//
//                // Data
//                writer.WritePropertyName("data");
//                writer.WriteValue(holder.Data);
//
//                writer.WriteEndObject();
//            }
//
//            public override WebSocketFailureMessage ReadJson(JsonReader reader, System.Type objectType,
//                WebSocketFailureMessage existingValue, bool hasExistingValue, JsonSerializer serializer)
//            {
//                var json = JObject.Load(reader);
//                var res = hasExistingValue ? existingValue : new WebSocketFailureMessage();
//                res.Kind = json["kind"].Value<string>();
//
//                var data = json["data"];
//                if (data != null)
//                {
//                    res.Cause = data["cause"].Value<string>();
//                    res.Data = data["data"].Value<string>();
//                }
//
//                return res;
//            }
//        }
}
