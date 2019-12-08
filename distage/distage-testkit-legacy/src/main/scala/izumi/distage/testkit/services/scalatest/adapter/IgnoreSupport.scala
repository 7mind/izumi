package izumi.distage.testkit.services.scalatest.adapter

import org.scalatest.TestCancellationLegacy

@deprecated("Use dstest", "2019/Jul/18")
private[testkit] trait IgnoreSupport {
  protected final def ignoreThisTest(cause: Throwable): Nothing = {
    ignoreThisTest(None, Some(cause))
  }

  protected final def ignoreThisTest(message: String): Nothing = {
    ignoreThisTest(Some(message), None)
  }

  protected final def ignoreThisTest(message: String, cause: Throwable): Nothing = {
    ignoreThisTest(Some(message), Some(cause))
  }

  protected final def ignoreThisTest(message: Option[String] = None, cause: Option[Throwable] = None): Nothing = {
    TestCancellationLegacy.cancel(message, cause)
  }

}
