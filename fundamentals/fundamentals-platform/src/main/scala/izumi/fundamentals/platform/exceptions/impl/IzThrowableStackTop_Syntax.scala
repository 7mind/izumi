package izumi.fundamentals.platform.exceptions.impl

final class IzThrowableStackTop_Syntax(t: Throwable, acceptedPackages: Set[String]) {

  def forPackages(acceptedPackages: Set[String]): IzThrowableStackTop_Syntax = {
    new IzThrowableStackTop_Syntax(t, this.acceptedPackages ++ acceptedPackages)
  }

  def shortTrace: String = {
    val messages = new IzThrowable_Syntax(t).allCauses.map {
      currentThrowable =>
        val origin = stackTop(currentThrowable) match {
          case Some(frame) =>
            s"${frame.getFileName}:${frame.getLineNumber}"
          case _ =>
            "?"
        }
        s"${currentThrowable.getMessage}@${currentThrowable.getClass.getSimpleName} $origin"
    }

    messages.mkString(", due ")
  }

  def stackTop: Option[StackTraceElement] = stackTop(t)

  def addAllSuppressed(suppressed: Iterable[Throwable]): Throwable = {
    suppressed.foreach(t.addSuppressed)
    t
  }

  private def stackTop(throwable: Throwable): Option[StackTraceElement] = {
    throwable.getStackTrace.find {
      frame =>
        !frame.isNativeMethod && acceptedPackages.exists(frame.getClassName.startsWith)
    }
  }

}
