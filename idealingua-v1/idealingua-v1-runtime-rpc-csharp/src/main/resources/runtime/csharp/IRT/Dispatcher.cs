
using System;
using System.Collections;
using System.Collections.Generic;

namespace IRT {
    public class Dispatcher<C, D> {
        private Dictionary<string, IServiceDispatcher<C, D>> services;

        public Dispatcher() {
            services = new Dictionary<string, IServiceDispatcher<C, D>>();
        }

        public bool Register(IServiceDispatcher<C, D> dispatcher) {
            var svc = dispatcher.GetSupportedService();
            if (services.ContainsKey(svc)) {
                return false;
            }

            services.Add(svc, dispatcher);
            return true;
        }

        public bool Unregister(string serviceName) {
            return services.Remove(serviceName);
        }

        public D Dispatch(C ctx, string service, string method, D data) {
            if (!services.ContainsKey(service)) {
                throw new DispatcherException(string.Format("Service {0} is not registered with the dispatcher.", service));
            }

            return services[service].Dispatch(ctx, method, data);
        }
    }
}
