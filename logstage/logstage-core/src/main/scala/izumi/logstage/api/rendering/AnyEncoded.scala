package izumi.logstage.api.rendering

import scala.language.implicitConversions

sealed trait AnyEncoded {
  type T <: Any
  def value: T
  def codec: Option[LogstageCodec[T]]
}

object AnyEncoded extends AnyEncodedLowPriority {
  @inline implicit def toPair[A](a: (String, A))(implicit maybeCodec: LogstageCodec[A] = null): (String, AnyEncoded) = (a._1, to(a._2))
}
sealed trait AnyEncodedLowPriority {
  @inline implicit def to[A](a: A)(implicit maybeCodec: LogstageCodec[A] = null): AnyEncoded = StrictEncoded.to(a)
}

sealed trait StrictEncoded extends AnyEncoded

object StrictEncoded extends StrictEncodedLowPriority {
  @inline implicit def toPair[A](a: (String, A))(implicit maybeCodec: LogstageCodec[A]): (String, StrictEncoded) = (a._1, to(a._2))
}
sealed trait StrictEncodedLowPriority {
  @inline implicit def to[A](a: A)(implicit maybeCodec: LogstageCodec[A]): StrictEncoded = new StrictEncoded {
    override type T = A
    override def value: A = a
    override def codec: Option[LogstageCodec[A]] = Option(maybeCodec)
  }
}
