package com.github.pshirshov.izumi.distage

import org.scalatest.WordSpec
import com.github.pshirshov.izumi.distage.model.definition.TypeLevelDSL
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

import scala.language.existentials

class TypeLevelDSLTest extends WordSpec {

  "Type-level DSL" should {
    "allow to define contexts" in {
      import com.github.pshirshov.izumi.distage.fixtures.BasicCases.BasicCase1._

      TypeLevelDSL.Bindings()
        .bind[TestClass]
        .bind[TestDependency0, TestImpl0]
    }

    "can reflect back from types" in {
      val z = new X {
        override def bark: String = "MOO"
      }

      RuntimeDIUniverse.u.typeOf[X]

      val singletonImpl = new TypeLevelDSL.ImplDef.InstanceImpl[X, z.type] {}
      assert(singletonImpl.repr.instance.asInstanceOf[X].bark == "MOO")

      val singletonImpl2 = TypeLevelDSL.ImplDef.InstanceImpl[X](z)
      assert(singletonImpl2.repr.instance.asInstanceOf[X].bark == "MOO")
    }

    "cannot reflect back from types when singleton value is out of scope" in {
      assertTypeError("""
        val singletonImpl = {
          val z = new X {
            override def bark: String = "MOO"
          }

          new TypeLevelDSL.ImplDef.InstanceImpl[X, z.type] {}
        }
        assert(singletonImpl.repr.instance.asInstanceOf[X].bark == "MOO")
        """
      )
    }
  }

  trait X {
    def bark: String = "Bark"
  }

}
