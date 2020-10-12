package izumi.distage.model.exceptions

abstract class DIException(message: String, cause: Throwable, captureStackTrace: Boolean) extends RuntimeException(message, cause, true, captureStackTrace) {
  def this(message: String, cause: Throwable) = this(message, cause, true)
  def this(message: String) = this(message, null)
}
