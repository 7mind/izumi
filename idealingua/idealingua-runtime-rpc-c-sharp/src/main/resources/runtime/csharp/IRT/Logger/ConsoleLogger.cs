
using System;
using IRT;

namespace IRT.Logger {
    public class ConsoleLogger: ILogger {
        private LogLevel level;

        public ConsoleLogger(LogLevel level) {
            this.level = level;
        }

        public void Logf(LogLevel level, string format, params object[] args) {
            if (this.level > level) {
        		return;
        	}

            var prefix = "[ " + level.ToString() + ", " + DateTime.Now.ToString() + " ]: ";
        	if (level == LogLevel.Error) {
        	    Console.WriteLine("******************** ERROR ********************\n");
        	    Console.WriteLine(prefix + format, args);
        	    Console.WriteLine("\n***********************************************");
        	} else {
        	    Console.WriteLine(prefix + format, args);
        	}
        }
    }
}
