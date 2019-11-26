package izumi.logstage.sink

import izumi.fundamentals.platform.language.Quirks
import izumi.logstage.api.Log
import izumi.logstage.api.logger.LogSink

import scala.concurrent.duration._

class QueueingSink(target: LogSink, sleepTime: FiniteDuration = 50.millis) extends LogSink with AutoCloseable {

  Quirks.discard(sleepTime)

  def start(): Unit = {}

  def flush(e: Log.Entry): Unit = {
    target.flush(e)
  }

  override def close(): Unit = {}

}

object QueueingSink {

  trait Step {
    def continue: Boolean

    def onStep(): Unit
  }

  class CountingStep(max: Int) extends Step {
    var counter: Int = 0

    override def continue: Boolean = counter <= max

    override def onStep(): Unit = counter += 1
  }

  object NullStep extends Step {
    override def continue: Boolean = true

    override def onStep(): Unit = {}
  }

}
