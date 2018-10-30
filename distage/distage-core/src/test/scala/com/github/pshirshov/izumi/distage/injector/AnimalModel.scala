package com.github.pshirshov.izumi.distage.injector

import com.github.pshirshov.izumi.distage.fixtures.BasicCases._
import distage._
import org.scalatest.WordSpec

class AnimalModel extends WordSpec with MkInjector {
  "animal model" must {
    "produce valid plans" in {
      import AnimalModel._
      val definition = new ModuleDef {
        make[Cluster]
        make[UserRepo].from[UserRepoImpl]
        make[AccountsRepo].from[AccountsRepoImpl]
        make[UsersService].from[UserServiceImpl]
        make[AccountingService].from[AccountingServiceImpl]
        make[UsersApiImpl]
        make[AccountsApiImpl]
        make[App]
      }

      val injector = Injector.Standard()
      val plan = injector.plan(definition)
      //    import com.github.pshirshov.izumi.distage.model.plan.CompactPlanFormatter._
      //    println()
      //    println(plan.render)
      //    println()
      //    println(plan.topology.dependencies.tree(DIKey.get[AccountsApiImpl]))
      val context = injector.produce(plan)
      assert(context.find[App].nonEmpty)
    }
  }
}
