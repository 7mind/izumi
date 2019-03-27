
namespace IRT.Transport.Authorization {
    public abstract class AuthMethod {
        public abstract bool FromValue(string value);
        public abstract string ToValue();
    }
}
