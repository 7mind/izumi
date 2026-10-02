package izumi.distage.testkit.scalatest

import org.scalatest.{Assertion, Succeeded}

object AssertionBridge {
  def apply(check: => Unit): Assertion = {
    check
    Succeeded
  }
}
