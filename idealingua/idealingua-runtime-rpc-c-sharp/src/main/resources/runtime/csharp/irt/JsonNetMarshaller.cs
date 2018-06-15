
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
}
