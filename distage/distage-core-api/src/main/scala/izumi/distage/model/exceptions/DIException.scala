package izumi.distage.model.exceptions

abstract class DIException(message: String, cause: Throwable) extends RuntimeException(message, cause) {
  def this(message: String) = this(message, null)
}
