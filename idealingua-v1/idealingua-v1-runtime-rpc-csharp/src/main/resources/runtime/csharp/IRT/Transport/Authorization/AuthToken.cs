
namespace IRT.Transport.Authorization {
    class AuthToken: AuthMethod {
        public string Token;

        public AuthToken(string token = null) {
            Token = token;
        }

        public override bool FromValue(string value) {
            return false;
        }

        public override string ToValue() {
            return "Bearer " + Token;
        }
    }
}