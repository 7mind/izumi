package com.github.pshirshov.izumi.distage.injector

import com.github.pshirshov.izumi.distage.Fixtures.{BasicCase1, SetCase1, SetCase2}
import com.github.pshirshov.izumi.distage.model.exceptions.TODOBindingException
import distage.ModuleDef
import org.scalatest.WordSpec

import scala.util.Try

class AdvancedBindingsTest extends WordSpec with MkInjector {

  "Support TODO bindings" in {
    import BasicCase1._

    val injector = mkInjector()

    val def1 = new ModuleDef {
      todo[TestDependency0]
    }
    val def2 = new ModuleDef {
      make[TestDependency0].todo
    }
    val def3 = new ModuleDef {
      make[TestDependency0].named("fug").todo
    }

    val plan1 = injector.plan(def1)
    val plan2 = injector.plan(def2)
    val plan3 = injector.plan(def3)

    assert(Try(injector.produce(plan1)).toEither.left.exists(_.getCause.isInstanceOf[TODOBindingException]))
    assert(Try(injector.produce(plan2)).toEither.left.exists(_.getCause.isInstanceOf[TODOBindingException]))
    assert(Try(injector.produce(plan3)).toEither.left.exists(_.getCause.isInstanceOf[TODOBindingException]))
  }

  "ModuleBuilder supports tags; same bindings with different tags are merged" in {
    import SetCase1._

    val definition = new ModuleDef {
      many[SetTrait].named("n1").tagged("A", "B")
        .add[SetImpl1].tagged("A")
        .add[SetImpl2].tagged("B")
        .add[SetImpl3].tagged("A") // merge
        .add[SetImpl3].tagged("B") // merge

      make[Service1].tagged("CA").from[Service1] // merge
      make[Service1].tagged("CB").from[Service1] // merge

      make[Service2].tagged("CC")

      many[SetTrait].tagged("A", "B")
    }

    assert(definition.bindings.size == 7)
    assert(definition.bindings.count(_.tags == Set("A", "B")) == 3)
    assert(definition.bindings.count(_.tags == Set("CA", "CB")) == 1)
    assert(definition.bindings.count(_.tags == Set("CC")) == 1)
    assert(definition.bindings.count(_.tags == Set("A")) == 1)
    assert(definition.bindings.count(_.tags == Set("B")) == 1)
  }

  "Tags in different modules are merged" in {
    import BasicCase1._

    val def1 = new ModuleDef {
      make[TestDependency0].tagged("a")
      make[TestDependency0].tagged("b")

      tag("1")
    }

    val def2 = new ModuleDef {
      tag("2")

      make[TestDependency0].tagged("x").tagged("y")
    }

    val definition = def1 ++ def2

    assert(definition.bindings.head.tags == Set("1", "2", "a", "b", "x", "y"))
  }

  "Tags in different overriden modules are merged" in {
    import BasicCase1._

    val def1 = new ModuleDef {
      make[TestDependency0].tagged("a").tagged("b")

      tag("1")
    }

    val def2 = new ModuleDef {
      tag("2")

      make[TestDependency0].tagged("x").tagged("y")
    }

    val definition = def1 overridenBy def2

    assert(definition.bindings.head.tags == Set("1", "2", "a", "b", "x", "y"))
  }

  "Set element references are the same as their referees" in {
    import SetCase2._

    val definition = new ModuleDef {
      make[Service1]

      many[Service]
        .ref[Service1]
    }

    val injector = mkInjector()
    val plan = injector.plan(definition)

    val context = injector.produce(plan)
    val svc = context.get[Service1]
    val set = context.get[Set[Service]]
    assert(set.head eq svc)
  }

}
