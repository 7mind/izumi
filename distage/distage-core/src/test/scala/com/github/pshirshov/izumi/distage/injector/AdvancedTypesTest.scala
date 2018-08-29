package com.github.pshirshov.izumi.distage.injector

import com.github.pshirshov.izumi.distage.fixtures.TraitCases._
import com.github.pshirshov.izumi.distage.fixtures.TypesCases._
import distage._
import org.scalatest.WordSpec

import scala.language.reflectiveCalls

class AdvancedTypesTest extends WordSpec with MkInjector {

  "support generics" in {
    import TypesCase1._

    val definition: ModuleBase = new ModuleDef {
      make[List[Dep]].named("As").from(List(DepA()))
      make[List[Dep]].named("Bs").from(List(DepB()))
      make[List[DepA]].from(List(DepA(), DepA(), DepA()))
      make[TestClass[DepA]]
    }

    val injector = mkInjector()
    val plan = injector.plan(definition)
    val context = injector.produce(plan)

    assert(context.get[List[Dep]]("As").forall(_.isInstanceOf[DepA]))
    assert(context.get[List[DepA]].forall(_.isInstanceOf[DepA]))
    assert(context.get[List[Dep]]("Bs").forall(_.isInstanceOf[DepB]))
    assert(context.get[TestClass[DepA]].inner == context.get[List[DepA]])
  }

  "support classes with typealiases" in {
    import TypesCase1._

    val definition = new ModuleDef {
      make[DepA]
      make[TestClass2[TypeAliasDepA]]
    }

    val injector = mkInjector()
    val plan = injector.plan(definition)
    val context = injector.produce(plan)

    assert(context.get[TestClass2[TypeAliasDepA]].inner.isInstanceOf[TypeAliasDepA])
  }

  "support traits with typealiases" in {
    import TypesCase1._

    val definition = new ModuleDef {
      make[DepA]
      make[TestTrait]
    }

    val injector = mkInjector()
    val plan = injector.plan(definition)
    val context = injector.produce(plan)

    assert(context.get[TestTrait].dep.isInstanceOf[TypeAliasDepA])
  }

  "type annotations in di keys do not result in different keys" in {
    import TraitCase2._

    val definition = new ModuleDef {
      make[Dependency1 @Id("special")]
      make[Trait1]
    }

    val injector = mkInjector()
    val plan = injector.plan(definition)
    val context = injector.produce(plan)

    val instantiated = context.get[Dependency1]
    val instantiated1 = context.get[Dependency1 @Id("special")]

    assert(instantiated eq instantiated1)
  }

  "handle `with` types" in {
    import TypesCase3._

    val definition = new ModuleDef {
      make[Dep]
      make[Dep2]
      make[Trait2 with Trait1].from[Trait6]
    }

    val injector = mkInjector()
    val plan = injector.plan(definition)
    val context = injector.produce(plan)

    val instantiated = context.get[Trait2 with Trait1]

    assert(instantiated.dep == context.get[Dep])
  }

  "handle refinement & structural types" in {
    import TypesCase3._

    val definition = new ModuleDef {
      make[Dep]
      make[Dep2]
      make[Trait1 { def dep: Dep2 }].from[Trait3[Dep2]]
      make[{def dep: Dep}].from[Trait6]
    }

    val injector = mkInjector()
    val plan = injector.plan(definition)
    val context = injector.produce(plan)

    val instantiated1 = context.get[Trait1 { def dep: Dep2 }]
    val instantiated2 = context.get[{def dep: Dep}]

    assert(instantiated1.dep == context.get[Dep2])
    assert(instantiated2.dep == context.get[Dep])
  }

  "handle function local type aliases" in {
    import TypesCase4._

    class Definition[T: Tag] extends ModuleDef {
      make[Dep]
      make[Dep2]
      val _ = {
        type X[A] = Trait1[Dep, A]
        make[X[T]]
      }
    }

    val injector = mkInjector()
    val plan = injector.plan(new Definition[Dep2])
    val context = injector.produce(plan)

    val instantiated = context.get[Trait1[Dep, Dep2]]

    assert(instantiated.a == context.get[Dep])
    assert(instantiated.b == context.get[Dep2])
  }

  "handle abstract structural refinement types" in {
    import TypesCase3._

    class Definition[T: Tag, G <: T { def dep: Dep }: Tag] extends ModuleDef {
      make[Dep]
      make[T { def dep: Dep }].from[G]
    }

    val definition = new Definition[Trait1, Trait1]

    val injector = mkInjector()
    val plan = injector.plan(definition)
    val context = injector.produce(plan)

    val instantiated = context.get[Trait1 { def dep: Dep }]

    assert(instantiated.dep == context.get[Dep])
  }

  "handle abstract `with` types" in {
    import TypesCase3._

    class Definition[T: Tag, G <: T with Trait1: Tag] extends ModuleDef {
      make[Dep]
      make[T with Trait1].from[G]
    }

    val definition = new Definition[Trait3[Dep], Trait3[Dep]]

    val injector = mkInjector()
    val plan = injector.plan(definition)
    val context = injector.produce(plan)

    val instantiated = context.get[Trait3[Dep] with Trait1]

    assert(instantiated.dep == context.get[Dep])
  }

  "handle generic parameters in abstract `with` types" in {
    import TypesCase3._

    class Definition[T <: Dep: Tag, K >: Trait5[T]: Tag] extends ModuleDef {
      make[T]
      make[Trait3[T] with K].from[Trait5[T]]
    }

    val definition = new Definition[Dep, Trait4]

    val injector = mkInjector()
    val plan = injector.plan(definition)
    val context = injector.produce(plan)

    val instantiated = context.get[Trait3[Dep] with Trait4]

    assert(instantiated.dep == context.get[Dep])
  }

  "progression test: Support type lambdas in TagK when part of lambda closes on a generic parameter with available Tag" in {
    assertTypeError("""
      def partialEitherTagK[A: Tag] = TagK[Either[A, ?]]

      print(partialEitherTagK[Int])
      assert(partialEitherTagK[Int] != null)
    """)
  }

}
