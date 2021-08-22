package izumi.logstage.api.rendering

import izumi.logstage.api.Log
import izumi.logstage.api.Log.LogContext

import scala.reflect.ClassTag

class MaskingLogEntryTransformer(maskingConfig: Map[String, MaskingLogEntryTransformer.LogMasking[?]]) extends LogEntryTransformer {
  override def apply(log: Log.Entry): Log.Entry = {
    val maskedMessage = log.message.copy(args = maskLogContext(log.message.args))
    val maskedContext = log.context.copy(
      customContext = log.context.customContext.copy(
        values = maskLogContext(log.context.customContext.values)
      )
    )

    log.copy(message = maskedMessage, context = maskedContext)
  }

  private def maskLogContext(context: LogContext): LogContext =
    context.map {
      logArg =>
        val maskedValue = maskingConfig
          .get(logArg.name)
          .flatMap(masking => masking.safeApply(logArg.value, logArg.codec))
          .getOrElse(logArg.value)
        logArg.copy(value = maskedValue)
    }
}

object MaskingLogEntryTransformer {

  def logMasking[T: ClassTag](f: (T, Option[LogstageCodec[T]]) => T): LogMasking[T] =
    new LogMasking[T](f)

  final class LogMasking[T] private[izumi] (f: (T, Option[LogstageCodec[T]]) => T)(implicit ctg: ClassTag[T]) {
    private[izumi] def apply(value: T, codec: Option[LogstageCodec[T]]): T = f(value, codec)

    private[izumi] def safeApply(value: Any, codec: Option[LogstageCodec[Any]]): Option[Any] =
      value match {
        case ctg(valueTyped) => Some(f(valueTyped, codec.asInstanceOf[Option[LogstageCodec[T]]]))
        case _ => None
      }
  }
}
