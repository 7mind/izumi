
using System;

namespace IRT.Transport.Client {
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
}