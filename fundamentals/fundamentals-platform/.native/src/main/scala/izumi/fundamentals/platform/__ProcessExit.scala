package izumi.fundamentals.platform

private[izumi] object __ProcessExit {
  def apply(code: Int): Unit = System.exit(code)
}
