package org.bitbucket.pshirshov.izumi.logger

import com.ratoshniuk.izumi.Log
import org.bitbucket.pshirshov.izumi.logger.api.Logger

trait WithLogContext {

  import Log._

  implicit val context: StaticContext = StaticContext(this.getClass.getCanonicalName)

  private val threadData = new ThreadLocal[ThreadData]() {
    override def initialValue(): ThreadData = {
      val thread = Thread.currentThread()
      ThreadData(thread.getName, thread.getId)
    }
  }

  implicit def thread: ThreadData = threadData.get()
}

class BoundLogger(logger: Logger)(implicit val context: WithLogContext) // api part
{

  import Log._

  def debug(message: Log.Message)(implicit custom: CustomContext = EmptyCustomContext): Unit = {
    withLogLevel(message, Log.Level.Debug)(custom)
  }

  def warn(message: Log.Message)(implicit custom: CustomContext = EmptyCustomContext): Unit = {
    withLogLevel(message, Log.Level.Warn)(custom)
  }

  def info(message: Log.Message)(implicit custom: CustomContext = EmptyCustomContext): Unit = {
    withLogLevel(message, Log.Level.Info)(custom)
  }

  def error(message: Log.Message)(implicit custom: CustomContext = EmptyCustomContext): Unit = {
    withLogLevel(message, Log.Level.Error)(custom)
  }

  private def withLogLevel(message: Log.Message, lvl : Log.Level)(implicit custom: CustomContext = EmptyCustomContext): Unit = {
    logger.log(Context(context.context, DynamicContext(lvl, context.thread), custom), message)
  }


}

