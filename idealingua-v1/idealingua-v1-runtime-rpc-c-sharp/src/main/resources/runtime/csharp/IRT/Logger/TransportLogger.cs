using System.Collections.Generic;

namespace IRT
{
    public interface ITransportLogger : ILogger
    {
        void LogRequest(string method, string url, string data, Dictionary<string, string> headers);

        void LogResponse(string method, string url, string response, long resultCode,
            Dictionary<string, string> headers);
    }
}
