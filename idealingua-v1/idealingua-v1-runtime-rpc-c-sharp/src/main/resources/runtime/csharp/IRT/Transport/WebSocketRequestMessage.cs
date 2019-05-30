
using System.Collections.Generic;
//using IRT.Marshaller;
//using Newtonsoft.Json;
//using Newtonsoft.Json.Linq;

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

    // [JsonConverter(typeof(WebSocketRequestMessage_JsonNetConverter))]
    public class WebSocketRequestMessageJson: WebSocketRequestMessage<string> {
        public WebSocketRequestMessageJson(string kind): base(kind){
        }
    }

//    public class WebSocketRequestMessage_JsonNetConverter : JsonNetConverter<WebSocketPackageMessageJson>
//        {
//            public override void WriteJson(JsonWriter writer, WebSocketPackageMessageJson holder, JsonSerializer serializer)
//            {
//                writer.WriteStartObject();
//                // Kind
//                writer.WritePropertyName("kind");
//                writer.WriteValue(holder.Kind);
//
//                // ID
//                writer.WritePropertyName("id");
//                writer.WriteValue(holder.Id);
//
//                // Service
//                if (!string.IsNullOrEmpty(holder.Service))
//                {
//                    writer.WritePropertyName("service");
//                    writer.WriteValue(holder.Service);
//                }
//
//                // Method
//                if (!string.IsNullOrEmpty(holder.Method))
//                {
//                    writer.WritePropertyName("method");
//                    writer.WriteValue(holder.Method);
//                }
//
//                // Headers
//                if (holder.Headers != null && holder.Headers.Count > 0)
//                {
//                    writer.WritePropertyName("headers");
//                    writer.WriteStartObject();
//                    foreach (var mkv in holder.Headers)
//                    {
//                        writer.WritePropertyName(mkv.Key);
//                        writer.WriteValue(mkv.Value);
//                    }
//                    writer.WriteEndObject();
//                }
//
//                // Data
//                if (!string.IsNullOrEmpty(holder.Data))
//                {
//                    writer.WritePropertyName("data");
//                    writer.WriteRawValue(holder.Data);
//                }
//                writer.WriteEndObject();
//            }
//
//            public override WebSocketPackageMessageJson ReadJson(JsonReader reader, System.Type objectType,
//                WebSocketPackageMessageJson existingValue, bool hasExistingValue, JsonSerializer serializer)
//            {
//                var json = JObject.Load(reader);
//
//                var kind = json["kind"].Value<string>();
//
//                var res = hasExistingValue ? existingValue : new WebSocketPackageMessageJson(kind);
//                res.Kind = kind;
//                res.Id = json["id"].Value<string>();
//                res.Service = json["service"] != null ? json["service"].Value<string>() : null;
//                res.Method = json["method"] != null ? json["method"].Value<string>() : null;
//
//                Dictionary<string, string> headers = null;
//                if (json["headers"] != null && json["headers"].Type != JTokenType.Null)
//                {
//                    headers = new Dictionary<string, string>();
//                    foreach (var header in ((JObject) json["headers"]).Properties())
//                    {
//                        headers.Add(header.Name, header.Value.Value<string>());
//                    }
//                }
//
//                // TODO See ResponseConverter message below, need to change to Reader and use Raw access
//                if (json["data"] != null)
//                {
//                    var dataObj = json["data"];
//                    res.Data = dataObj.ToString();
//                }
//                return res;
//            }
//        }
}
