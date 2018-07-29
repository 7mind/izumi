package com.github.pshirshov.izumi.distage.injector

import com.github.pshirshov.izumi.distage.Fixtures.{BasicCase1, BasicCase2, BasicCase3, SetCase1}
import com.github.pshirshov.izumi.distage.model.definition.Binding.SingletonBinding
import com.github.pshirshov.izumi.distage.model.definition.{Binding, ImplDef}
import com.github.pshirshov.izumi.distage.model.exceptions.{ProvisioningException, UnsupportedWiringException, UntranslatablePlanException}
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.ImportDependency
import distage.{DIKey, ModuleBase, ModuleDef, SafeType}
import org.scalatest.WordSpec

class BasicTest extends WordSpec with MkInjector {


  "maintain correct operation order" in {
    import BasicCase1._
    val definition: ModuleBase = new ModuleDef {
      make[TestClass]
      make[TestDependency3]
      make[TestDependency0].from[TestImpl0]
      make[TestDependency1]
      make[TestCaseClass]
      make[LocatorDependent]
      make[TestInstanceBinding].from(TestInstanceBinding())
    }

    val injector = mkInjector()
    val plan = injector.plan(definition)
    assert(plan.steps.exists(_.isInstanceOf[ImportDependency]))

    val exc = intercept[ProvisioningException] {
      injector.produce(plan)
    }

    assert(exc.getMessage.startsWith("Operations failed (1)"))

    val fixedPlan = plan.resolveImports {
      case i if i.target == DIKey.get[NotInContext] => new NotInContext {}
    }
    val locator = injector.produce(fixedPlan)
    assert(locator.get[LocatorDependent].ref.get == locator)
  }

  "support multiple bindings" in {
    import BasicCase1._
    val definition: ModuleBase = new ModuleDef {
      many[JustTrait].named("named.empty.set")

      many[JustTrait]
        .add[JustTrait]
        .add(new Impl1)

      many[JustTrait].named("named.set")
        .add(new Impl2())

      many[JustTrait].named("named.set")
        .add[Impl3]
    }

    val injector = mkInjector()
    val plan = injector.plan(definition)
    val context = injector.produce(plan)

    assert(context.get[Set[JustTrait]].size == 2)
    assert(context.get[Set[JustTrait]]("named.empty.set").isEmpty)
    assert(context.get[Set[JustTrait]]("named.set").size == 2)
  }

  "support named bindings" in {
    import BasicCase2._
    val definition: ModuleBase = new ModuleDef {
      make[TestClass]
        .named("named.test.class")
      make[TestDependency0].from[TestImpl0Bad]
      make[TestDependency0].named("named.test.dependency.0")
        .from[TestImpl0Good]
      make[TestInstanceBinding].named("named.test")
        .from(TestInstanceBinding())
    }

    val injector = mkInjector()
    val plan = injector.plan(definition)
    val context = injector.produce(plan)

    assert(context.get[TestClass]("named.test.class").correctWired())
  }


  "fail on unbindable" in {
    import BasicCase3._

    val definition: ModuleBase = new ModuleBase {
      override def bindings: Set[Binding] = Set(
        SingletonBinding(DIKey.get[Dependency], ImplDef.TypeImpl(SafeType.get[Long]))
      )
    }

    val injector = mkInjector()
    intercept[UnsupportedWiringException] {
      injector.plan(definition)
    }
  }

  "fail on unsolvable conflicts" in {
    import BasicCase3._

    val definition: ModuleBase = new ModuleDef {
      make[Dependency].from[Impl1]
      make[Dependency].from[Impl2]
    }

    val injector = mkInjector()
    val exc = intercept[UntranslatablePlanException] {
      injector.plan(definition)
    }
    assert(exc.conflicts.size == 1 && exc.conflicts.contains(DIKey.get[Dependency]))
  }

  // BasicProvisionerTest
  "instantiate simple class" in {
    import BasicCase1._
    val definition: ModuleBase = new ModuleDef {
      make[TestCaseClass2]
      make[TestInstanceBinding].from(new TestInstanceBinding)
    }

    val injector = mkInjector()
    val plan = injector.plan(definition)
    val context = injector.produce(plan)
    val instantiated = context.get[TestCaseClass2]

    assert(instantiated.a.z.nonEmpty)
  }

  "handle set bindings ordering" in {
    import SetCase1._

    val definition = new ModuleDef {
      make[Service2]
      make[Service0]
      make[Service1]
      make[Service3]

      many[SetTrait]
        .add[SetImpl1]
        .add[SetImpl2]
        .add[SetImpl3]

      many[SetTrait].named("n1")
        .add[SetImpl1]
        .add[SetImpl2]
        .add[SetImpl3]

      many[SetTrait].named("n2")
        .add[SetImpl1]
        .add[SetImpl2]
        .add[SetImpl3]

      many[SetTrait].named("n3")
        .add[SetImpl1]
        .add[SetImpl2]
        .add[SetImpl3]
    }

    val injector = mkInjector()
    val plan = injector.plan(definition)

    val context = injector.produce(plan)

    assert(context.get[Service0].set.size == 3)
    assert(context.get[Service1].set.size == 3)
    assert(context.get[Service2].set.size == 3)
    assert(context.get[Service3].set.size == 3)
  }


}
