
using System;
using System.Collections;
using System.Collections.Generic;

namespace IRT {
    public class DispatcherException: Exception {
        public DispatcherException() {
        }

        public DispatcherException(string message): base(message) {
        }

        public DispatcherException(string message, Exception inner): base(message, inner) {
        }
    }
}
