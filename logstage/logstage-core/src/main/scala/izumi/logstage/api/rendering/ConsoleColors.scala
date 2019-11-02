package izumi.logstage.api.rendering

import izumi.logstage.api.Log

object ConsoleColors {
  def logLevelColor(lvl: Log.Level): String = lvl match {
    case Log.Level.Trace => Console.MAGENTA
    case Log.Level.Debug => Console.BLUE
    case Log.Level.Info => Console.GREEN
    case Log.Level.Warn => Console.CYAN
    case Log.Level.Error => Console.YELLOW
    case Log.Level.Crit => Console.RED
  }
}
