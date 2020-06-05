package izumi.fundamentals.platform.exceptions

import scala.collection.mutable.ArrayBuffer
import scala.language.implicitConversions

class IzThrowable(t: Throwable, acceptedPackages: Set[String]) {

  import IzThrowable._

  def forPackages(acceptedPackages: Set[String]): IzThrowable = {
    new IzThrowable(t, this.acceptedPackages ++ acceptedPackages)
  }

  def stackTrace: String = {
    import java.io.{PrintWriter, StringWriter}
    val sw = new StringWriter
    val pw = new PrintWriter(sw)
    t.printStackTrace(pw)
    sw.toString
  }

  def shortTrace: String = {
    val messages = t.allCauses.map {
      currentThrowable =>
        val origin = currentThrowable.stackTop match {
          case Some(frame) =>
            s"${frame.getFileName}:${frame.getLineNumber}"
          case _ =>
            "?"
        }
        s"${currentThrowable.getMessage}@${currentThrowable.getClass.getSimpleName} $origin"
    }

    messages.mkString(", due ")
  }

  def allMessages: Seq[String] = {
    t.allCauses.map(_.getMessage)
  }

  def allCauseClassNames: Seq[String] = {
    t.allCauses.map(_.getClass.getName)
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

  def stackTop: Option[StackTraceElement] = {
    t.getStackTrace.find {
      frame =>
        !frame.isNativeMethod && acceptedPackages.exists(frame.getClassName.startsWith)
    }
  }

  def addAllSuppressed(suppressed: Iterable[Throwable]): Throwable = {
    suppressed.foreach(t.addSuppressed)
    t
  }
}

object IzThrowable {
  implicit def toRichThrowable(throwable: Throwable): IzThrowable = new IzThrowable(throwable, Set.empty)
}
