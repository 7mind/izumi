
using System;
using System.Collections;
using System.Collections.Generic;

namespace irt {
    public class DispatcherException: Exception {
        public DispatcherException() {
        }

        public DispatcherException(string message): base(message) {
        }

        public DispatcherException(string message, Exception inner): base(message, inner) {
        }
    }

    public interface IServiceDispatcher<C, D> {
        D Dispatch(C ctx, string method, D data);
        string GetSupportedService();
        string[] GetSupportedMethods();
    }

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
            if (!services.ContainsKey(serviceName)) {
                return false;
            }

            services.Remove(serviceName);
            return true;
        }

        public D Dispatch(C ctx, string service, string method, D data) {
            if (!services.ContainsKey(service)) {
                throw new DispatcherException(string.Format("Service {0} is not registered with the dispatcher.", service));
            }

            return services[service].Dispatch(ctx, method, data);
        }
    }
}
