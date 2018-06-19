
using System;

namespace irt {
    public class TransportException: Exception {
        public TransportException() {
        }

        public TransportException(string message): base(message) {
        }

        public TransportException(string message, Exception inner): base(message, inner) {
        }
    }

    public interface ITransportCallback {
    }

    public class TransportCallback<T> : ITransportCallback {
        public Action<Exception> OnFailureHandler;
        public Action<T> OnSuccessHandler;
        public Action FinalHandler;

        public TransportCallback(Action<T> onSuccess, Action<Exception> onFailure, Action final = null) {
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

        public static TransportCallback<T> Empty { get {return new TransportCallback<T>(onSuccess => {}, onFailure => {});} }
    }

    public interface ITransport<C> where C: class {
        void Send<I, O>(string service, string method, I payload, TransportCallback<O> callback, C ctx = null);
    }
}