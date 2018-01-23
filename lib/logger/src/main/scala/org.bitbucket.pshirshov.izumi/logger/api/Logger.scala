package org.bitbucket.pshirshov.izumi.logger.api

import org.bitbucket.pshirshov.izumi.logger.Log._
import org.bitbucket.pshirshov.izumi.logger.{Log, LogConfigService}

// SCALA API
trait Logger {

  import Log._

  def log(context: Log.Context, message: Log.Message): Unit = route(Entry(message, context))

  def debug(message: Log.Message)(implicit context: StaticContext, dynamic: ThreadData, custom: CustomContext = EmptyCustomContext): Unit = {
    log(Context(context, DynamicContext(Level.Debug, dynamic), custom), message)
  }

  protected def logConfigService: LogConfigService

  protected def route(entry: Entry): Unit = {
    logConfigService.config(entry).foreach {
      mapping =>
        if (mapping.filter.accept(entry)) {
          mapping.sink.flush(entry)
        }
    }
  }
}

object Logger {

  implicit class LogSC(val sc: StringContext) {
    def l(args: Any*): Message = Message(sc, args: _*)
  }

}

// don't forget, we need this
trait MacroLogger extends Logger {
  // def debug(template: String, values: Map[String, String]): Unit = macro ...
  // this should perform early filtering, like
  //    if (level >= logConfigService.level(staticContext)) { super.log(...) }
}