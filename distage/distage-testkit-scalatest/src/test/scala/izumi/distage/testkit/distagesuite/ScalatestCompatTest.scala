package izumi.distage.testkit.distagesuite

import izumi.distage.testkit.scalatest.{AssertZIO, Spec1}
import izumi.fundamentals.platform.functional.Identity
import org.scalatest.exceptions.TestFailedException
import org.scalatest.matchers.{must, should}

final class ScalatestCompatTestShould extends Spec1[Identity] with should.Matchers with AssertZIO {

  "test" should {
    "start" in {
      intercept[TestFailedException](1 should equal(5))
      1 should equal(1)
      1 should equal(1)
      1 should not equal 2
    }
  }

}

final class ScalatestCompatTestMust extends Spec1[Identity] with must.Matchers with AssertZIO {

  "test" should {
    "start" in {
      intercept[TestFailedException](1 must equal(5))
      1 must equal(1)
      1 must equal(1)
      1 must not equal 2
    }
  }

}
