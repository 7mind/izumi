package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.fixtures.BasicCases.BasicCase1
import com.github.pshirshov.izumi.distage.model.definition.LocatorDef
import com.github.pshirshov.izumi.distage.model.exceptions.LocatorDefUninstantiatedBindingException
import org.scalatest.WordSpec

class LocatorDefTest extends WordSpec {

  "LocatorDef" should {
    "support singleton instances" in {
      import BasicCase1._

      val testDependency0 = new TestImpl0
      val testDependency1: TestDependency1 = new TestDependency1 {
        override def unresolved: NotInContext = new NotInContext {}
      }
      val testInstanceBinding = TestInstanceBinding()
      val testCaseClass2 = TestCaseClass2(testInstanceBinding)

      val ctx = new LocatorDef {
        make[TestDependency0].from(testDependency0)
        make[TestDependency1].from(testDependency1)
        make[TestInstanceBinding].from(testInstanceBinding)
        make[TestCaseClass2].named("fug").from(testCaseClass2)
      }

      assert(ctx.get[TestDependency0] == testDependency0)
      assert(ctx.get[TestDependency1] == testDependency1)
      assert(ctx.get[TestInstanceBinding] == testInstanceBinding)
      assert(ctx.get[TestCaseClass2]("fug") == testCaseClass2)
      assert(ctx.find[TestCaseClass2].isEmpty)

      assert(ctx.instances.map(_.value) == Seq(testDependency0, testDependency1, testInstanceBinding, testCaseClass2))
    }

    "not have access to advanced operations" in {
      assertTypeError(
        """
          import BasicCase1._

          new LocatorDef { make[JustTrait].from[Impl0] }"""
      )
    }

    "die on undefined instance" in {
      import BasicCase1._

      val ctx = new LocatorDef {
        make[TestCaseClass]
      }
      assertThrows[LocatorDefUninstantiatedBindingException](ctx.instances)
    }

    "support empty sets" in {
      import BasicCase1._

      val ctx = new LocatorDef {
        many[TestDependency0]
        many[TestDependency1].named("fug")
      }

      assert(ctx.get[Set[TestDependency0]] == Set())
      assert(ctx.get[Set[TestDependency1]]("fug") == Set())

      assert(ctx.find[Set[TestDependency1]].isEmpty)
      assert(ctx.find[Set[TestCaseClass2]].isEmpty)
    }

    "support sets" in {
      import BasicCase1._

      val ctx = new LocatorDef {
        many[TestInstanceBinding].named("r")
          .add(TestInstanceBinding())
          .add(TestInstanceBinding(
            """ Ma-ma-magic in her eyes, Leandoer's paradise
              | All my, all my, boys off drugs, I've been lost in this life""".stripMargin))

        many[TestInstanceBinding]
          .add(TestInstanceBinding())
      }

      assert(ctx.instances.size == 2)
      assert(ctx.get[Set[TestInstanceBinding]]("r").size == 2)
      assert(ctx.get[Set[TestInstanceBinding]] == Set(TestInstanceBinding()))
    }

  }

}
