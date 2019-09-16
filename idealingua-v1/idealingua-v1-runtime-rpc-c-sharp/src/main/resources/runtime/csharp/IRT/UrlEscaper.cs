using System.Web;

namespace IRT.Transport
{
    public static class UrlEscaper
    {
        public static string Escape(string source)
        {
            return HttpUtility.UrlEncode(source);
        }

        public static string UnEscape(string source)
        {
            return HttpUtility.UrlDecode(source);
        }
    }
}
