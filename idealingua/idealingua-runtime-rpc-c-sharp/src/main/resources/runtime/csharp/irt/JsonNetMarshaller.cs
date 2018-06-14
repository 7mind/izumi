
using System;
using Newtonsoft.Json;

namespace irt {
    public class JsonNetMarshaller: IJsonMarshaller {
        public string Marshal<I>(I data) {
            return JsonConvert.SerializeObject(data);
        }

        public O Unmarshal<O>(string data) {
            return JsonConvert.DeserializeObject<O>(data);
        }
    }
}