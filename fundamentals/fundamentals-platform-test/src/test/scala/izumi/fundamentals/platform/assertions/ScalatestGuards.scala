package izumi.fundamentals.platform.assertions

import org.scalatest.Assertions

trait ScalatestGuards extends PlatformGuards with Assertions {
  override def broken(f: => Any): Unit = {
    intercept[Throwable](f)
    ()
  }
}
