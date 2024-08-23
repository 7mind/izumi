package izumi.distage.roles.launcher

import izumi.distage.model.exceptions.runtime.ProvisioningException
import izumi.distage.roles.model.exceptions.DIAppBootstrapException

trait AppFailureHandler {
  def onError(t: Throwable): Unit
}

object AppFailureHandler {

  object TerminatingHandler extends AppFailureHandler {
    override def onError(t: Throwable): Nothing = {
      report(t)
      System.exit(1)
      rethrow(t)
    }
  }

  object NullHandler extends AppFailureHandler {
    override def onError(t: Throwable): Unit = {
      rethrow(t)
    }
  }

  private def rethrow(t: Throwable): Nothing = {
    t match {
      case d: ProvisioningException =>
        // here we remove suppressed exceptions to make output more readable
        throw new ProvisioningException(d.getMessage, captureStackTrace = false)
      case o =>
        throw o
    }
  }

  private def report(t: Throwable): Unit = {
    t match {
      case d: ProvisioningException =>
        d.getSuppressed.toList.headOption match {
          case Some(d: DIAppBootstrapException) =>
            System.err.println(d.getMessage)
          case _ => // JVM will print the exception regardless
        }
      case _ =>
    }
  }

}
