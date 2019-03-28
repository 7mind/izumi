
namespace IRT.Transport.Authorization {
    class AuthCustom: AuthMethod {
        public string Value;

        public AuthCustom(string value = null) {
            Value = value;
        }

        public override bool FromValue(string value) {
            Value = value;
            return true;
        }

        public override string ToValue() {
            return Value;
        }
    }
}