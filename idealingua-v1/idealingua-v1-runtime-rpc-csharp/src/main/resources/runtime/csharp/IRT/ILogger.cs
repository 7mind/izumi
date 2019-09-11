
using System;

namespace IRT {
    public enum LogLevel {
        Trace,
        Debug,
        Info,
        Warning,
        Error
    }

    public interface ILogger {
        void Logf(LogLevel level, string format, params object[] args);
    }
}
