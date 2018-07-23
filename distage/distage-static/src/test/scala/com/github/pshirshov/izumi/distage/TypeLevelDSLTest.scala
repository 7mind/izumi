package com.github.pshirshov.izumi.distage

import org.scalatest.WordSpec

class TypeLevelDSLTest extends WordSpec {

  "Type-level DSL" should {
    "allow to define contexts" in {

      assertCompiles("""
        import com.github.pshirshov.izumi.distage.Fixtures.Case1._
        import com.github.pshirshov.izumi.distage.model.definition.TypeLevelDSL

        TypeLevelDSL.Bindings()
          .bind[TestClass]
          .bind[TestDependency0, TestImpl0]
      """)
    }
  }

}
