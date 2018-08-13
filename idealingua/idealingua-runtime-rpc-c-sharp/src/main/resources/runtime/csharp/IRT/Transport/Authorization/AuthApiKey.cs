
namespace IRT.Transport.Authorization {
    class AuthApiKey: AuthMethod {
        public string ApiKey;

        public AuthApiKey(string apiKey = null) {
            ApiKey = apiKey;
        }

        public override bool FromValue(string value) {
            return false;
        }

        public override string ToValue() {
            return "ApiKey " + ApiKey;
        }
    }
}