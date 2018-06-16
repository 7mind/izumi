
using System;
using Newtonsoft.Json;
using Newtonsoft.Json.Converters;

namespace irt {
    public class JsonNetMarshaller: IJsonMarshaller {
        private JsonSerializerSettings settings;

        public JsonNetMarshaller(bool pretty = false) {
            settings = new JsonSerializerSettings();
            settings.Converters.Add(new StringEnumConverter());
            settings.NullValueHandling = NullValueHandling.Ignore;
            settings.TypeNameHandling = TypeNameHandling.None;
            settings.Formatting = pretty ? Formatting.Indented : Formatting.None;
        }

        public string Marshal<I>(I data) {
            return JsonConvert.SerializeObject(data, settings);
        }

        public O Unmarshal<O>(string data) {
            return JsonConvert.DeserializeObject<O>(data, settings);
        }
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
            base.DateTimeFormat = "yyyy-MM-ddTHH:mm:ss.FFFFFFFzzz";
        }
    }

    class JsonNetDateTimeUTCConverter : IsoDateTimeConverter {
        public JsonNetDateTimeUTCConverter() {
            base.DateTimeFormat = "yyyy-MM-ddTHH:mm:ss.FFFFFFFZ";
        }
    }
}
