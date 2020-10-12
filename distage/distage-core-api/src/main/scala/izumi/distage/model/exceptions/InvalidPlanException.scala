package izumi.distage.model.exceptions

class InvalidPlanException(message: String, val omitClassName: Boolean, captureStackTrace: Boolean) extends DIException(message, null, captureStackTrace) {
  def this(message: String) = this(message, false, true)
  override def toString: String = if (omitClassName) getMessage else super.toString
}
