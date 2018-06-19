
using System;
using Newtonsoft.Json;
using Newtonsoft.Json.Converters;
using Newtonsoft.Json.Serialization;

namespace irt {
    public class JsonNetMarshaller: IJsonMarshaller {
        private JsonSerializerSettings settings;

        public JsonNetMarshaller(bool pretty = false) {
            settings = new JsonSerializerSettings();
            settings.Converters.Add(new StringEnumConverter());
            settings.NullValueHandling = NullValueHandling.Ignore;
            settings.TypeNameHandling = TypeNameHandling.None;
            settings.ReferenceLoopHandling = ReferenceLoopHandling.Serialize;
            settings.DateParseHandling = DateParseHandling.None;
            settings.Formatting = pretty ? Formatting.Indented : Formatting.None;
        }

        public string Marshal<I>(I data) {
            // Console.WriteLine("Type: " + typeof(I).ToString() + ". Data: " + data.ToString());
            if (typeof(I).IsInterface) {
                if (!(data is IRTTI)) {
                    throw new Exception("Trying to serialize an interface which doesn't expose an IRTTI interface: " + typeof(I).ToString());
                }
                return JsonConvert.SerializeObject(data, typeof(I), settings);
            } else {
                return JsonConvert.SerializeObject(data, typeof(I), settings);
            }
        }

        public O Unmarshal<O>(string data) {
            return JsonConvert.DeserializeObject<O>(data, settings);
        }
    }

    internal sealed class InterfaceContractResolver : DefaultContractResolver
    {
        protected override JsonContract CreateContract(Type objectType)
        {
            Console.WriteLine("Contract: " + objectType.ToString());
            JsonContract contract = base.CreateContract(objectType);

            if (typeof(IRTTI).IsAssignableFrom(objectType)) {
                Console.WriteLine("Adding interface serializer: " + objectType.ToString());
                contract.Converter = new InterfaceJsonNetSerializer();
            }

            return contract;
        }
    }

    public class InterfaceJsonNetSerializer: JsonConverter {
        public override void WriteJson(JsonWriter writer, object value, JsonSerializer serializer) {
            IRTTI v = (IRTTI)value;
            writer.WriteStartObject();
            writer.WritePropertyName(v.GetFullClassName());
            serializer.Serialize(writer, v);
            writer.WriteEndObject();
        }

        public override object ReadJson(JsonReader reader, System.Type objectType, object existingValue, JsonSerializer serializer) {
            throw new Exception("InterfaceJsonNetSerializer should not be used for deserialization.");
        }

        public override bool CanConvert(System.Type objectType) {
            return false;
        }
    }

    public static class JsonNetTimeFormats {
        public static readonly string TslDefault = "yyyy-MM-ddTHH:mm:ss.ffffffzzz";
        public static readonly string[] Tsl = new string[] {
                    "yyyy-MM-ddTHH:mm:ss.ffffffzzz",
                    "yyyy-MM-ddTHH:mm:ss.fffzzz",
                    "yyyy-MM-ddTHH:mm:sszzz"
                };

        public static readonly string TszDefault = "yyyy-MM-ddTHH:mm:ss.ffffffZ";
        public static readonly string[] Tsz = new string[] {
                    "yyyy-MM-ddTHH:mm:ss.ffffffZ",
                    "yyyy-MM-ddTHH:mm:ss.fffZ",
                    "yyyy-MM-ddTHH:mm:ssZ"
                };
    }

    class JsonNetDateConverter : IsoDateTimeConverter {
        public JsonNetDateConverter() {
            base.DateTimeFormat = "yyyy-MM-dd";
        }
    }

    class JsonNetTimeConverter : IsoDateTimeConverter {
        public JsonNetTimeConverter() {
            base.DateTimeFormat = "HH:mm:ss";
        }
    }

    class JsonNetDateTimeLocalConverter : IsoDateTimeConverter {
        public JsonNetDateTimeLocalConverter() {
            base.DateTimeFormat = "yyyy-MM-ddTHH:mm:ss.FFFFFFzzz";
        }
    }

    class JsonNetDateTimeUTCConverter : IsoDateTimeConverter {
        public JsonNetDateTimeUTCConverter() {
            base.DateTimeFormat = "yyyy-MM-ddTHH:mm:ss.FFFFFFZ";
        }
    }
}
