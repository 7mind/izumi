
using System;
using Io.Protoforce.Auth;
using Newtonsoft.Json;
using Newtonsoft.Json.Converters;
using Newtonsoft.Json.Serialization;
using IRT.Transport;
using Newtonsoft.Json.Linq;

namespace IRT.Marshaller {
    public class JsonNetMarshaller: IJsonMarshaller {
        private JsonSerializerSettings settings;
        private JsonConverter[] webSocketConverters;

        public JsonNetMarshaller(bool pretty = false) {
            settings = new JsonSerializerSettings();
            settings.Converters.Add(new StringEnumConverter());
            settings.NullValueHandling = NullValueHandling.Ignore;
            settings.TypeNameHandling = TypeNameHandling.None;
            settings.ReferenceLoopHandling = ReferenceLoopHandling.Serialize;
            settings.DateParseHandling = DateParseHandling.None;
            settings.Formatting = pretty ? Formatting.Indented : Formatting.None;
            webSocketConverters = new JsonConverter[] {
                new WebSocketRequestMessage_JsonNetConverter(),
                new WebSocketResponseMessage_JsonNetConverter()
            };
        }

        public string Marshal<I>(I data) {
            var ti = typeof(I);
            // For void responses, we need to provide something that wouldn't break the marshaller
            if (ti == typeof(IRT.Void)) {
                return "{}";
            }

            if (data is WebSocketRequestMessageJson || data is WebSocketResponseMessageJson) {
                return JsonConvert.SerializeObject(data, webSocketConverters);
            }

            if (ti.IsInterface) {
                if (!(data is IRTTI)) {
                    throw new Exception("Trying to serialize an interface which doesn't expose an IRTTI interface: " + typeof(I).ToString());
                }
                return JsonConvert.SerializeObject(new InterfaceMarshalWorkaround(data as IRTTI), settings);
            } else {
                return JsonConvert.SerializeObject(data, typeof(I), settings);
            }
        }

        public O Unmarshal<O>(string data) {
            var to = typeof(O);
            if (to == typeof(WebSocketRequestMessageJson) || to == typeof(WebSocketResponseMessageJson)) {
                return JsonConvert.DeserializeObject<O>(data, webSocketConverters);
            }
            
            return JsonConvert.DeserializeObject<O>(data, settings);
        }

        [JsonConverter(typeof(InterfaceMarshalWorkaround_JsonNetConverter))]
        private class InterfaceMarshalWorkaround {
            public IRTTI Value;
            public InterfaceMarshalWorkaround(IRTTI value) {
                Value = value;
            }
        }

        private class InterfaceMarshalWorkaround_JsonNetConverter: JsonNetConverter<InterfaceMarshalWorkaround> {
            public override void WriteJson(JsonWriter writer, InterfaceMarshalWorkaround holder, JsonSerializer serializer) {
                // Serializing polymorphic type
                writer.WriteStartObject();
                writer.WritePropertyName(holder.Value.GetFullClassName());
                serializer.Serialize(writer, holder.Value);
                writer.WriteEndObject();

            }

            public override InterfaceMarshalWorkaround ReadJson(JsonReader reader, System.Type objectType, InterfaceMarshalWorkaround existingValue, bool hasExistingValue, JsonSerializer serializer) {
                throw new Exception("Should not be used for Reading, workaround only for writing.");
            }
        }

        private class WebSocketRequestMessage_JsonNetConverter: JsonNetConverter<WebSocketRequestMessageJson> {
            public override void WriteJson(JsonWriter writer, WebSocketRequestMessageJson holder, JsonSerializer serializer) {
                writer.WriteStartObject();
                // ID
                writer.WritePropertyName("id");
                writer.WriteValue(holder.ID);
                
                // Service
                if (!string.IsNullOrEmpty(holder.Service)) {
                    writer.WritePropertyName("service");
                    writer.WriteValue(holder.Service);
                }
                
                // Method
                if (!string.IsNullOrEmpty(holder.Method)) {
                    writer.WritePropertyName("method");
                    writer.WriteValue(holder.Method);
                }
                
                // Authorization
                if (!string.IsNullOrEmpty(holder.Authorization)) {
                    writer.WritePropertyName("authorization");
                    writer.WriteValue(holder.Authorization);
                }
                
                // Data
                if (!string.IsNullOrEmpty(holder.Data)) {
                    writer.WritePropertyName("data");
                    writer.WriteRawValue(holder.Data);
                }
                writer.WriteEndObject();

            }

            public override WebSocketRequestMessageJson ReadJson(JsonReader reader, System.Type objectType, WebSocketRequestMessageJson existingValue, bool hasExistingValue, JsonSerializer serializer) {
                var json = JObject.Load(reader);

                var res = new WebSocketRequestMessageJson();
                res.ID = json["id"].Value<string>();
                res.Service = json["service"] != null ? json["service"].Value<string>() : null;
                res.Method = json["method"] != null ? json["method"].Value<string>() : null;
                res.Authorization = json["authorization"] != null ? json["authorization"].Value<string>() : null;
                // TODO See ResponseConverter message below, need to change to Reader and use Raw access
                if (json["data"] != null) {
                    var dataObj = json["data"];
                    res.Data = dataObj.ToString();
                }
                return res;
            }
        }
        
        private class WebSocketResponseMessage_JsonNetConverter: JsonNetConverter<WebSocketResponseMessageJson> {
            public override void WriteJson(JsonWriter writer, WebSocketResponseMessageJson holder, JsonSerializer serializer) {
                writer.WriteStartObject();
                // Ref
                writer.WritePropertyName("ref");
                writer.WriteValue(holder.Ref);
                
                // Error
                if (!string.IsNullOrEmpty(holder.Error)) {
                    writer.WritePropertyName("error");
                    writer.WriteValue(holder.Error);
                }
                
                // Data
                if (!string.IsNullOrEmpty(holder.Data)) {
                    writer.WritePropertyName("data");
                    writer.WriteRawValue(holder.Data);
                }
                writer.WriteEndObject();
            }

            public override WebSocketResponseMessageJson ReadJson(JsonReader reader, System.Type objectType, WebSocketResponseMessageJson existingValue, bool hasExistingValue, JsonSerializer serializer) {
                var json = JObject.Load(reader);
                var res = hasExistingValue ? existingValue : new WebSocketResponseMessageJson();
                res.Ref = json["ref"].Value<string>();
                res.Error = json["error"] != null ? json["error"].Value<string>() : null;
                if (json["data"] != null) {
                    var dataObj = json["data"];
                    res.Data = dataObj.ToString();
                }
                /*
                // TODO Avoid unnecessary double parsing and emitting, should use raw reader
                while (reader.Read()) {
                    if (reader.Value != null) {
                        if (reader.TokenType == JsonToken.PropertyName) {
                            Console.WriteLine("Found: " + reader.Value);
                            
                            switch (reader.Value) {
                                case "ref": res.Ref = reader.ReadAsString();
                                    break;
                                case "error": res.Error = reader.ReadAsString();
                                    break;
                                case "data":
                                    var currentDepth = reader.Depth;
                                    while (reader.Read() && reader.Depth > currentDepth) {
                                        reader.
                                    }
                                    var raw = JRaw.Create(reader);;
                                    res.Data = reader.ReadAsBytes().ToString();// raw.ToString();
                                    Console.WriteLine(res.Data);
                                    break;
                            }
                        }
                    }
                }
                */
                return res;
            }
        }
    }

    // From here https://github.com/JamesNK/Newtonsoft.Json/blob/master/Src/Newtonsoft.Json/JsonConverter.cs
    public abstract class JsonNetConverter<T> : JsonConverter {
        public sealed override void WriteJson(JsonWriter writer, object value, JsonSerializer serializer) {
            WriteJson(writer, (T)value, serializer);
        }

        public abstract void WriteJson(JsonWriter writer, T value, JsonSerializer serializer);

        public sealed override object ReadJson(JsonReader reader, Type objectType, object existingValue, JsonSerializer serializer) {
            return ReadJson(reader, objectType, default(T), false, serializer);
        }

        public abstract T ReadJson(JsonReader reader, Type objectType, T existingValue, bool hasExistingValue, JsonSerializer serializer);

        public sealed override bool CanConvert(Type objectType) {
            return typeof(T).IsAssignableFrom(objectType);
        }
    }

    public static class JsonNetTimeFormats {
        public static readonly string TslDefault = "yyyy-MM-ddTHH:mm:ss.fff";
        public static readonly string[] Tsl = new string[] {
                    "yyyy-MM-ddTHH:mm:ss",
                    "yyyy-MM-ddTHH:mm:ss.f",
                    "yyyy-MM-ddTHH:mm:ss.ff",
                    "yyyy-MM-ddTHH:mm:ss.fff",
                    "yyyy-MM-ddTHH:mm:ss.ffff",
                    "yyyy-MM-ddTHH:mm:ss.fffff",
                    "yyyy-MM-ddTHH:mm:ss.ffffff",
                    "yyyy-MM-ddTHH:mm:ss.fffffff",
                    "yyyy-MM-ddTHH:mm:ss.ffffffff",
                    "yyyy-MM-ddTHH:mm:ss.fffffffff"
                };

        public static readonly string TszDefault = "yyyy-MM-ddTHH:mm:ss.fffzzz";
        public static readonly string[] Tsz = new string[] {
                   "yyyy-MM-ddTHH:mm:ssZ",
                   "yyyy-MM-ddTHH:mm:ss.fZ",
                   "yyyy-MM-ddTHH:mm:ss.ffZ",
                   "yyyy-MM-ddTHH:mm:ss.fffZ",
                   "yyyy-MM-ddTHH:mm:ss.ffffZ",
                   "yyyy-MM-ddTHH:mm:ss.fffffZ",
                   "yyyy-MM-ddTHH:mm:ss.ffffffZ",
                   "yyyy-MM-ddTHH:mm:ss.fffffffZ",
                   "yyyy-MM-ddTHH:mm:ss.ffffffffZ",
                   "yyyy-MM-ddTHH:mm:ss.fffffffffZ",
                   "yyyy-MM-ddTHH:mm:sszzz",
                   "yyyy-MM-ddTHH:mm:ss.fzzz",
                   "yyyy-MM-ddTHH:mm:ss.ffzzz",
                   "yyyy-MM-ddTHH:mm:ss.fffzzz",
                   "yyyy-MM-ddTHH:mm:ss.ffffzzz",
                   "yyyy-MM-ddTHH:mm:ss.fffffzzz",
                   "yyyy-MM-ddTHH:mm:ss.ffffffzzz",
                   "yyyy-MM-ddTHH:mm:ss.fffffffzzz",
                   "yyyy-MM-ddTHH:mm:ss.ffffffffzzz",
                   "yyyy-MM-ddTHH:mm:ss.fffffffffzzz"
                };

        public static readonly string TsuDefault = "yyyy-MM-ddTHH:mm:ss.fffZ";
        public static readonly string[] Tsu = JsonNetTimeFormats.Tsz;
    }

    class JsonNetDateConverter : IsoDateTimeConverter {
        public JsonNetDateConverter() {
            base.DateTimeFormat = "yyyy-MM-dd";
        }
    }

    class JsonNetTimeConverter : IsoDateTimeConverter {
        public JsonNetTimeConverter() {
            base.DateTimeFormat = "HH:mm:ss.fff";
        }
    }

    class JsonNetDateTimeLocalConverter : IsoDateTimeConverter {
        public JsonNetDateTimeLocalConverter() {
            base.DateTimeFormat = "yyyy-MM-ddTHH:mm:ss.fff";
        }
    }

    class JsonNetDateTimeZonedConverter : IsoDateTimeConverter {
        public JsonNetDateTimeZonedConverter() {
            base.DateTimeFormat = "yyyy-MM-ddTHH:mm:ss.fffzzz";
        }
    }
}
