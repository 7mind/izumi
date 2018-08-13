
using System;

namespace IRT.Transport {
    public class TransportException: Exception {
        public TransportException() {
        }

        public TransportException(string message): base(message) {
        }

        public TransportException(string message, Exception inner): base(message, inner) {
        }
    }
}