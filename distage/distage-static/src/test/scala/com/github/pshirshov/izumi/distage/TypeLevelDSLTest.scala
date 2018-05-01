package com.github.pshirshov.izumi.distage

import org.scalatest.WordSpec

class TypeLevelDSLTest extends WordSpec {

  "Type-level DSL" should {
    "allow to define contexts" in {

      assertCompiles("""
        import com.github.pshirshov.izumi.distage.Fixtures._
        import com.github.pshirshov.izumi.distage.model.definition.static.TypeLevelDSL
        import Case1._

        TypeLevelDSL.Bindings()
          .bind[TestClass]
          .bind[TestDependency0, TestImpl0]
      """)
    }
  }

}
