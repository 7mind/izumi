
namespace IRT.Transport.Authorization {
    class AuthBasic: AuthMethod {
        public string User;
        public string Pass;

        public AuthBasic(string user = null, string pass = null) {
            User = user;
            Pass = pass;
        }

        public override bool FromValue(string value) {
            return false;
        }

        public override string ToValue() {
            return "Basic " + User + ":" + Pass;
        }
    }
}