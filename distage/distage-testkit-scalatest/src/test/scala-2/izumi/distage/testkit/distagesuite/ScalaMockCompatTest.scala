package izumi.distage.testkit.distagesuite

import distage.ModuleDef
import izumi.distage.testkit.TestConfig
import izumi.distage.testkit.scalatest.Spec1
import izumi.fundamentals.platform.functional.Identity
import org.scalamock.scalatest.MockFactory

final class ScalaMockCompatTest extends Spec1[Identity] with MockFactory {

  trait TestClass {
    def method: String
  }

  override protected def config: TestConfig = super.config.copy(
    moduleOverrides = new ModuleDef {
      make[TestClass].from(mock[TestClass])
    }
  )

  "mockfactory" should {
    "be compatible" in {
      (testMock: TestClass) =>
        (() => testMock.method).expects().returning("hello")
        assert(testMock.method == "hello")
    }
  }

}
