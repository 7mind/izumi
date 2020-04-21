package izumi.distage.dsl

import izumi.distage.fixtures.BasicCases.BasicCase1
import izumi.distage.model.definition.{Id, LocatorDef}
import izumi.distage.model.exceptions.LocatorDefUninstantiatedBindingException
import org.scalatest.wordspec.AnyWordSpec

class LocatorDefTest extends AnyWordSpec {

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
        make[TestDependency0].fromValue(testDependency0)
        make[TestDependency1].fromValue(testDependency1)
        make[TestInstanceBinding].fromValue(testInstanceBinding)
        make[TestCaseClass2].named("fug").fromValue(testCaseClass2)
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
          .addValue(TestInstanceBinding())
          .addValue(TestInstanceBinding("value 2"))

        many[TestInstanceBinding]
          .addValue(TestInstanceBinding())
      }

      assert(ctx.instances.size == 2)
      assert(ctx.get[Set[TestInstanceBinding]]("r").size == 2)
      assert(ctx.get[Set[TestInstanceBinding]] == Set(TestInstanceBinding()))
    }

    "support addImplicit" in {
      val ctx = new LocatorDef {
        addImplicit[DummyImplicit]
        addImplicit[DummyImplicit].named("dummy")
      }

      assert(ctx.instances.size == 2)
      assert(ctx.get[DummyImplicit] ne null)
      assert(ctx.get[DummyImplicit]("dummy") ne null)
    }

    ".run and .runOption works" in {
      val ctx = new LocatorDef {
        make[Int].fromValue(5)
      }

      assert(ctx.run { i: Int => i + 5 } == 10)
      assert(ctx.runOption { i: Int => i + 5 } == Some(10))
      assert(ctx.runOption { i: Int @Id("special") => i }.isEmpty)
    }

    ".run and .runOption work for by-name lambdas" in {
      val ctx = new LocatorDef {
        make[Int].fromValue(5)
      }

      def l1(i: => Int) = i + 5
      def l2(i: => Int @Id("special")) = i

      assert(ctx.run(l1 _) == 10)
      assert(ctx.runOption(l1 _) == Some(10))
      assert(ctx.runOption(l2 _).isEmpty)
    }

  }

}
