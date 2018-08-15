
using System;

namespace IRT.Transport.Authorization {
    class AuthApiKey: AuthMethod {
        public string ApiKey;

        public AuthApiKey(string apiKey = null) {
            ApiKey = apiKey;
        }

        public override bool FromValue(string value) {
            var lower = value.ToLower();
            if (value.StartsWith("api-key ", StringComparison.Ordinal)) {
                ApiKey = value.Substring(8);
                return true;
            }

            if (value.StartsWith("apikey ", StringComparison.Ordinal)) {
                ApiKey = value.Substring(7);
                return true;
            }

            return false;
        }

        public override string ToValue() {
            return "Api-Key " + ApiKey;
        }
    }
}
