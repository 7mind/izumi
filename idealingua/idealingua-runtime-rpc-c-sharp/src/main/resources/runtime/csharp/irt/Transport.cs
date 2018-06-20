
using System;

namespace irt {
    public interface IClientTransportContext {
    }

    public class TransportException: Exception {
        public TransportException() {
        }

        public TransportException(string message): base(message) {
        }

        public TransportException(string message, Exception inner): base(message, inner) {
        }
    }

    public interface IClientTransportCallback {
    }

    public class ClientTransportCallback<T> : IClientTransportCallback {
        public Action<Exception> OnFailureHandler;
        public Action<T> OnSuccessHandler;
        public Action FinalHandler;

        public ClientTransportCallback(Action<T> onSuccess, Action<Exception> onFailure, Action final = null) {
            OnSuccessHandler = onSuccess;
            OnFailureHandler = onFailure;
            FinalHandler = final;
        }

        public void Success(T result) {
            if (OnSuccessHandler != null) {
                OnSuccessHandler(result);
            }

            if (FinalHandler != null) {
                FinalHandler();
            }
        }

        public void Failure(Exception exception) {
            if (OnFailureHandler != null) {
                OnFailureHandler(exception);
            }

            if (FinalHandler != null) {
                FinalHandler();
            }
        }

        public static ClientTransportCallback<T> Empty { get {return new ClientTransportCallback<T>(onSuccess => {}, onFailure => {});} }
    }

    public interface IClientTransport<C> where C: class, IClientTransportContext {
        void Send<I, O>(string service, string method, I payload, ClientTransportCallback<O> callback, C ctx = null);
    }
}