
namespace IRT.Transport.Authorization {
    abstract class AuthMethod {
        public abstract bool FromValue(string value);
        public abstract string ToValue();
    }
}