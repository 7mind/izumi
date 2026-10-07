package izumi.fundamentals.platform.assertions

import izumi.distage.testkit.runner.spec.TestAssertions

trait ScalatestGuards extends PlatformGuards {
  private val assertions: TestAssertions = new TestAssertions {}

  override def broken(f: => Any): Unit = {
    assertions.intercept[Throwable](f)
    ()
  }
}
