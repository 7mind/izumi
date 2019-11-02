
using System;
using IRT;

namespace IRT.Logger {
    public class DummyLogger: ILogger {
        public void Logf(LogLevel level, string format, params object[] args) {
        }
    }
}
