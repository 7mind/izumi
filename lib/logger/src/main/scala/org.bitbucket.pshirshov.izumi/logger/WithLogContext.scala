package org.bitbucket.pshirshov.izumi.logger

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
    //
    logger.log(Context(context.context, DynamicContext(Level.Debug, context.thread), custom), message)
  }
}

