
using System;
using IRT.Transport;

namespace IRT.Transport.Client {
    public class ClientTransportCallback<T> : IClientTransportCallback {
        public Action<TransportException> OnFailureHandler;
        public Action<T> OnSuccessHandler;
        public Action FinalHandler;

        public ClientTransportCallback(Action<T> onSuccess, Action<TransportException> onFailure, Action final = null) {
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

        public void Failure(TransportException exception) {
            if (OnFailureHandler != null) {
                OnFailureHandler(exception);
            }

            if (FinalHandler != null) {
                FinalHandler();
            }
        }

        public static ClientTransportCallback<T> Empty { get {return new ClientTransportCallback<T>(onSuccess => {}, onFailure => {});} }
    }
}