
namespace IRT.Transport.Server {
    public class ConnectionContext<C> {
        public SystemContext System;
        public C User;
    }
}