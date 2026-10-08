package izumi.fundamentals.platform

private[izumi] object __ProcessExit {
  def apply(code: Int): Unit = throw new UnsupportedOperationException(s"Process termination with code $code is unavailable on Scala.js")
}
