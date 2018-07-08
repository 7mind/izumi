
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
            if (typeof(I).IsInterface) {
                if (!(data is IRTTI)) {
                    throw new Exception("Trying to serialize an interface which doesn't expose an IRTTI interface: " + typeof(I).ToString());
                }
                return JsonConvert.SerializeObject(new InterfaceMarshalWorkaround(data as IRTTI), settings);
            } else {
                return JsonConvert.SerializeObject(data, typeof(I), settings);
            }
        }

        public O Unmarshal<O>(string data) {
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
                    "yyyy-MM-ddTHH:mm:ss.fff"
                };

        public static readonly string TszDefault = "yyyy-MM-ddTHH:mm:ss.fffzzz";
        public static readonly string[] Tsz = new string[] {
                    "yyyy-MM-ddTHH:mm:ss.fffzzz"
                };
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
