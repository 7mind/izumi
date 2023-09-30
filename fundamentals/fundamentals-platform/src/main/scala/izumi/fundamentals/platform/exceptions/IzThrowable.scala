package izumi.fundamentals.platform.exceptions

import scala.collection.mutable.ArrayBuffer
import scala.language.implicitConversions

final class IzThrowable(private val t: Throwable) extends AnyVal {

  def stacktraceString: String = {
    import java.io.{PrintWriter, StringWriter}
    val sw = new StringWriter
    val pw = new PrintWriter(sw)
    t.printStackTrace(pw)
    sw.toString
  }

  // this doesn't play well with intellisense
  @deprecated("use .stacktraceString")
  def stackTrace: String = {
    stacktraceString
  }

  def allMessages: Seq[String] = {
    allCauses.map(_.getMessage)
  }

  def allCauseClassNames: Seq[String] = {
    allCauses.map(_.getClass.getName)
  }

  def allCauses: Seq[Throwable] = {
    val ret = new ArrayBuffer[Throwable]()
    var currentThrowable = t
    while (currentThrowable != null) {
      ret.append(currentThrowable)
      currentThrowable = currentThrowable.getCause
    }
    ret.toSeq // 2.13 compat
  }

}

final class IzThrowableStackTop(t: Throwable, acceptedPackages: Set[String]) {

  def forPackages(acceptedPackages: Set[String]): IzThrowableStackTop = {
    new IzThrowableStackTop(t, this.acceptedPackages ++ acceptedPackages)
  }

  def shortTrace: String = {
    val messages = new IzThrowable(t).allCauses.map {
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

  private[this] def stackTop(throwable: Throwable): Option[StackTraceElement] = {
    throwable.getStackTrace.find {
      frame =>
        !frame.isNativeMethod && acceptedPackages.exists(frame.getClassName.startsWith)
    }
  }

}

object IzThrowable {
  implicit def toRichThrowable(throwable: Throwable): IzThrowable = new IzThrowable(throwable)
  implicit def toRichThrowableStackTop(throwable: Throwable): IzThrowableStackTop = new IzThrowableStackTop(throwable, Set.empty)
}
