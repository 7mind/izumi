import com.github.pshirshov.izumi.logstage.api
import com.github.pshirshov.izumi.logstage.sink

package object logstage {
  type IzLogger = api.IzLogger
  val IzLogger: api.IzLogger.type = api.IzLogger

  type ConsoleSink = sink.ConsoleSink
  val ConsoleSink: sink.ConsoleSink.type = sink.ConsoleSink

  type QueueingSink = sink.QueueingSink
  val QueueingSink: sink.QueueingSink.type = sink.QueueingSink
}
