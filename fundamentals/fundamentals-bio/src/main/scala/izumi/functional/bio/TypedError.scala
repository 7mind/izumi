package izumi.functional.bio

final case class TypedError[+A](prefixMessage: String, error: A)
  extends RuntimeException(s"${prefixMessage}Typed error of class=${error.getClass.getName}: $error", null, true, false)

object TypedError {
  def apply[A](error: A): TypedError[A] = new TypedError("", error)
}
