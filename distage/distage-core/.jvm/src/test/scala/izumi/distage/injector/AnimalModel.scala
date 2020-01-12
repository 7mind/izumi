package izumi.distage.injector

import distage._
import izumi.distage.model.PlannerInput
import izumi.distage.planning.extensions.GraphDumpBootstrapModule
import org.scalatest.WordSpec

class AnimalModel extends WordSpec with MkInjector {
  "animal model" must {
    "produce valid plans" in {
      import AnimalModel._
      val definition = PlannerInput(new ModuleDef {
        make[Cluster]
        make[UserRepo].from[UserRepoImpl]
        make[AccountsRepo].from[AccountsRepoImpl]
        make[UsersService].from[UserServiceImpl]
        make[AccountingService].from[AccountingServiceImpl]
        make[UsersApiImpl]
        make[AccountsApiImpl]
        make[UnrequiredDep]
        make[App]
      }, GCMode(DIKey.get[App]))

      val debug = false

      val injector = if (debug) {
        Injector.Standard(GraphDumpBootstrapModule())
      } else {
        Injector.Standard()
      }

      val plan = injector.plan(definition)

      if (debug) {
        println()
        println(plan.render)
        println()
        println(plan.renderDeps(plan.topology.dependencies.tree(DIKey.get[AccountsApiImpl])))
        println(plan.renderAllDeps())
        println()
      }
      val context = injector.produceUnsafe(plan)
      assert(context.find[App].nonEmpty)
    }
  }

}

object AnimalModel {
  class UnrequiredDep(val accountsRepo: AccountsRepo)
  class Cluster
  trait UsersService
  trait AccountingService
  trait UserRepo
  trait AccountsRepo

  class UserRepoImpl(val cluster: Cluster) extends UserRepo
  class AccountsRepoImpl(val cluster: Cluster) extends AccountsRepo

  class UserServiceImpl(val userRepo: UserRepo) extends UsersService
  class AccountingServiceImpl(val accountsRepo: AccountsRepo) extends AccountingService
  // cyclic dependency
  class UsersApiImpl(val service: UsersService, val accountsApi: AccountsApiImpl)
  class AccountsApiImpl(val service: AccountingService, val usersApi: UsersApiImpl)
  class App(val uapi: UsersApiImpl, val aapi: AccountsApiImpl)
}

