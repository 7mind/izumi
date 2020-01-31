package izumi.logstage.api

import izumi.logstage.api.Log.CustomContext
import izumi.logstage.api.logger.{AbstractMacroLogger, LogRouter, RoutingLogger}
import izumi.logstage.api.rendering.LogstageCodec

class IzLogger
(
  override val router: LogRouter,
  override val customContext: Log.CustomContext
) extends RoutingLogger with AbstractMacroLogger {

  override def withCustomContext(context: CustomContext): IzLogger = new IzLogger(router, customContext + context)
  final def withCustomContext(context: (String, AnyEncoded)*): IzLogger = withCustomContext(context.toMap)
  final def withCustomContext(context: Map[String, AnyEncoded]): IzLogger = withCustomContext(CustomContext(context))

  final def apply(context: CustomContext): IzLogger = withCustomContext(context)
  final def apply(context: (String, AnyEncoded)*): IzLogger = withCustomContext(context.toMap)
  final def apply(context: Map[String, AnyEncoded]): IzLogger = withCustomContext(context)
}

object IzLogger extends IzLoggerConvenienceApi {
  override type Logger = IzLogger

  protected def make(r: LogRouter, context: CustomContext): Logger = {
    new IzLogger(r, context)
  }
}

sealed trait AnyEncoded {
  type T
  def value: T
  def codec: Option[LogstageCodec[T]]
}
sealed trait StrictEncoded extends AnyEncoded

trait StrictEncodedLow {
  @inline implicit def to[A](a: A)(implicit maybeCodec: LogstageCodec[A]): StrictEncoded = new StrictEncoded {
    override type T = A

    override def value: A = a

    override def codec: Option[LogstageCodec[A]] = Option(maybeCodec)
  }

}

object StrictEncoded extends StrictEncodedLow {

  @inline implicit def toPair[A](a: (String, A))(implicit maybeCodec: LogstageCodec[A]): (String, StrictEncoded) = (a._1, to(a._2))
}

trait AnyEncodedLow {
  @inline implicit def to[A](a: A)(implicit maybeCodec: LogstageCodec[A] = null): AnyEncoded = StrictEncoded.to(a)

}

object AnyEncoded extends AnyEncodedLow {

  @inline implicit def toPair[A](a: (String, A))(implicit maybeCodec: LogstageCodec[A] = null): (String, AnyEncoded) = (a._1, to(a._2))

}
