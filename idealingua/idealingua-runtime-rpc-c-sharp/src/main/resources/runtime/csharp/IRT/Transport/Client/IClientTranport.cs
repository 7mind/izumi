
using System;

namespace IRT.Transport.Client {
    public interface IClientTransport<C> where C: class, IClientTransportContext {
        void Send<I, O>(string service, string method, I payload, ClientTransportCallback<O> callback, C ctx = null);
    }
}