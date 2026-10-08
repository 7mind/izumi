package izumi.distage.sbt

private[sbt] object HostFailures {
  def collect[A](values: IterableOnce[A])(operation: A => Unit): Option[Throwable] = {
    var failure = Option.empty[Throwable]
    values.iterator.foreach { value =>
      try operation(value)
      catch { case cause: Throwable => failure match {
        case Some(previous) => previous.addSuppressed(cause)
        case None => failure = Some(cause)
      } }
    }
    failure
  }

  def cleanup(original: Option[Throwable])(operation: => Unit): Unit = {
    try operation
    catch { case cause: Throwable => original match {
      case Some(previous) => previous.addSuppressed(cause)
      case None => throw cause
    } }
  }
}
