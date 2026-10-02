package izumi.fundamentals.platform.language

import izumi.fundamentals.testkit.AnyWordSpec

class SourcePackageMaterializerTest extends AnyWordSpec {

  "SourcePackageMaterializer" should {

    "return correct package name" in {
      val pkg = SourcePackageMaterializer.materialize
      assert(pkg.get.pkg == "izumi.fundamentals.platform.language")
    }

  }

}
