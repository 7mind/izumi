package izumi.fundamentals.platform.exceptions.impl

import scala.collection.mutable.ArrayBuffer

final class IzThrowable_Syntax(private val t: Throwable) extends AnyVal {

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
