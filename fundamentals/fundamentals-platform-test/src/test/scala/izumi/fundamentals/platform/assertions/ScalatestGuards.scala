package izumi.fundamentals.platform.assertions

import izumi.distage.testkit.runner.spec.TestAssertions

trait ScalatestGuards extends PlatformGuards with TestAssertions {
  override def broken(f: => Any): Unit = {
    intercept[Throwable](f)
    ()
  }
}
