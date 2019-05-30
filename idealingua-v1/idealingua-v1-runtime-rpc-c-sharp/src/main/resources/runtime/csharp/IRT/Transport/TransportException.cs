
using System;

namespace IRT.Transport {
    public class TransportException: Exception {
        public readonly long ResponseCode;
        public TransportException() {
        }

        public TransportException(string message): base(message) {
        }

        public TransportException(string message, long code) : base(message) {
            ResponseCode = code;
        }

        public TransportException(string message, Exception inner): base(message, inner) {
        }
    }

    public class RequestTimeoutException : TransportException {
        public RequestTimeoutException() : base("Request timed out.") {
        }
    }

    public class ConnectionClosedException : TransportException {
        public ConnectionClosedException() : base("Connection was closed.") {
        }
    }

    public class UnauthorizedException : TransportException {
        public UnauthorizedException(string message, long code) : base(message, code) {
        }
    }

    public class BadConnectionException : TransportException {
        public BadConnectionException(string message) : base(message) {
        }
    }

    public class TransportMarshallingException: TransportException {
        public TransportMarshallingException() {
        }

        public TransportMarshallingException(string message): base(message) {
        }

        public TransportMarshallingException(string message, Exception inner): base(message, inner) {
        }
    }
}