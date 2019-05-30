
//using IRT.Marshaller;
//using Newtonsoft.Json;
//using Newtonsoft.Json.Linq;

namespace IRT.Transport {
    public class WebSocketResponseMessage<D>: WebSocketMessageBase {
        public string Ref;
        public D Data;

        public WebSocketResponseMessage(string kind): base(kind){
        }
    }

    // [JsonConverter(typeof(WebSocketResponseMessage_JsonNetConverter))]
    public class WebSocketResponseMessageJson: WebSocketResponseMessage<string> {
        public WebSocketResponseMessageJson(string kind): base(kind){
        }
    }

//    public class WebSocketResponseMessage_JsonNetConverter : JsonNetConverter<WebSocketResponseMessageJson>
//        {
//            public override void WriteJson(JsonWriter writer, WebSocketResponseMessageJson holder,
//                JsonSerializer serializer)
//            {
//                writer.WriteStartObject();
//                // Kind
//                writer.WritePropertyName("kind");
//                writer.WriteValue(holder.Kind);
//
//                // Ref
//                writer.WritePropertyName("ref");
//                writer.WriteValue(holder.Ref);
//
//                // Data
//                if (!string.IsNullOrEmpty(holder.Data))
//                {
//                    writer.WritePropertyName("data");
//                    writer.WriteRawValue(holder.Data);
//                }
//
//                writer.WriteEndObject();
//            }
//
//            public override WebSocketResponseMessageJson ReadJson(JsonReader reader, System.Type objectType,
//                WebSocketResponseMessageJson existingValue, bool hasExistingValue, JsonSerializer serializer)
//            {
//                var json = JObject.Load(reader);
//                var kind = json["kind"].Value<string>();
//
//                var res = hasExistingValue ? existingValue : new WebSocketResponseMessageJson(kind);
//                res.Ref = json["ref"].Value<string>();
//                if (json["data"] != null)
//                {
//                    var dataObj = json["data"];
//                    res.Data = dataObj.ToString();
//                }
//                /*
//                // TODO Avoid unnecessary double parsing and emitting, should use raw reader
//                while (reader.Read()) {
//                    if (reader.Value != null) {
//                        if (reader.TokenType == JsonToken.PropertyName) {
//                            Console.WriteLine("Found: " + reader.Value);
//
//                            switch (reader.Value) {
//                                case "ref": res.Ref = reader.ReadAsString();
//                                    break;
//                                case "error": res.Error = reader.ReadAsString();
//                                    break;
//                                case "data":
//                                    var currentDepth = reader.Depth;
//                                    while (reader.Read() && reader.Depth > currentDepth) {
//                                        reader.
//                                    }
//                                    var raw = JRaw.Create(reader);;
//                                    res.Data = reader.ReadAsBytes().ToString();// raw.ToString();
//                                    Console.WriteLine(res.Data);
//                                    break;
//                            }
//                        }
//                    }
//                }
//                */
//                return res;
//            }
//        }
}
