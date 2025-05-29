package izumi.functional.bio

final case class TypedError[+A](prefixMessage: String, error: A)
  extends RuntimeException(s"${prefixMessage}Typed error of class=${error.getClass.getName}: $error", null, true, false)

object TypedError {
  def noMessage[A](error: A): TypedError[A] = new TypedError("", error)

  def wrapIfNotThrowable(error: Any): Throwable = {
    error match {
      case e: Throwable => e
      case e => TypedError.noMessage(e)
    }
  }

//  @deprecated("Renamed to `noMessage`", "1.2.18")
//  def apply[A](error: A): TypedError[A] = noMessage(error)
}
