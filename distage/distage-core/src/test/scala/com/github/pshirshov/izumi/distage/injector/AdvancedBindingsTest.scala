package com.github.pshirshov.izumi.distage.injector

import com.github.pshirshov.izumi.distage.fixtures.BasicCases.BasicCase1
import com.github.pshirshov.izumi.distage.fixtures.SetCases.{SetCase1, SetCase2}
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

    assert(Try(injector.produce(plan1)).toEither.left.exists(_.getSuppressed.head.isInstanceOf[TODOBindingException]))
    assert(Try(injector.produce(plan2)).toEither.left.exists(_.getSuppressed.head.isInstanceOf[TODOBindingException]))
    assert(Try(injector.produce(plan3)).toEither.left.exists(_.getSuppressed.head.isInstanceOf[TODOBindingException]))
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
