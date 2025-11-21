package izumi.distage.roles.launcher

import izumi.distage.model.exceptions.runtime.ProvisioningException
import izumi.distage.roles.model.exceptions.DIAppBootstrapException

trait AppFailureHandler {
  def onError(t: Throwable): Unit
}

object AppFailureHandler {

  open class TerminatingHandler(sysExit: Int => Unit) extends AppFailureHandler {
    override def onError(t: Throwable): Nothing = {
      report(t)
      sysExit(1)
      rethrow(t)
    }
  }
  object TerminatingHandler extends TerminatingHandler(sysExit = System.exit)

  object NullHandler extends AppFailureHandler {
    override def onError(t: Throwable): Nothing = {
      rethrow(t)
    }
  }

  private def rethrow(t: Throwable): Nothing = {
    t match {
      case p: ProvisioningException =>
        throw formatProvisioningException(p)
      case o =>
        throw o
    }
  }

  private def report(t: Throwable): Unit = {
    t match {
      case p: ProvisioningException =>
        p.getSuppressed.toList match {
          case (d: DIAppBootstrapException) :: Nil =>
            System.err.println(d.getMessage)
          case _ =>
            formatProvisioningException(p).printStackTrace()
        }
      case _ =>
        t.printStackTrace()
    }
  }

  private def formatProvisioningException(p: ProvisioningException): ProvisioningException = {
    // here we remove suppressed exceptions to make output more readable (ProvisioningException already includes other exceptions' stacktraces in getMessage)
    new ProvisioningException(p.getMessage, captureStackTrace = false)
  }

}
