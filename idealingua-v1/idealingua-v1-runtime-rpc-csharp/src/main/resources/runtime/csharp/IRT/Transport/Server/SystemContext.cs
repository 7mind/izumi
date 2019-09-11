
using IRT.Transport.Authorization;

namespace IRT.Transport.Server {
    public class SystemContext {
        public AuthMethod Auth;
        
        public bool UpdateAuth(string value) {
            if (string.IsNullOrEmpty(value)) {
                Auth = null;
                return true;
            }

            if (value.StartsWith("Bearer ")) {
                Auth = new AuthToken();
            } else 
            if (value.StartsWith("Basic ")) {
                Auth = new AuthBasic();
            } else {
                var lower = value.ToLowerInvariant();
                if (lower.StartsWith("api-key") || lower.StartsWith("apikey")) {
                    Auth = new AuthApiKey();
                } else {
                    Auth = new AuthCustom();
                }
            }

            return Auth.FromValue(value);
        }
    }
}