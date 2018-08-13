
using System;
using IRT.Transport.Authorization;

namespace IRT.Transport.Client {
    public interface IClientTransport<C> where C: class, IClientTransportContext {
        void Send<I, O>(string service, string method, I payload, ClientTransportCallback<O> callback, C ctx = null);
        void SetAuthorization(AuthMethod method);
    }
}