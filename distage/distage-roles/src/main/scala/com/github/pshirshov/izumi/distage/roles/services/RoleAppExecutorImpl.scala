package com.github.pshirshov.izumi.distage.roles.services

import com.github.pshirshov.izumi.distage.app.DiAppBootstrapException
import com.github.pshirshov.izumi.distage.config.ResolvedConfig
import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect.syntax._
import com.github.pshirshov.izumi.distage.model.plan.{DependencyGraph, DependencyKind, PlanTopologyImmutable}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.distage.plugins.MergedPlugins
import com.github.pshirshov.izumi.distage.roles._
import com.github.pshirshov.izumi.distage.roles.services.RoleAppPlanner.AppStartupPlans
import com.github.pshirshov.izumi.fundamentals.platform.cli.RoleAppArguments
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.logstage.api.IzLogger
import distage.{DIKey, Injector, Module, OrderedPlan, PlannerInput, TagK}


class RoleAppExecutorImpl[F[_] : TagK : DIEffect](
                                                   protected val hook: ApplicationShutdownStrategy[F],
                                                   roles: RolesInfo,
                                                   injector: Injector,
                                                   lateLogger: IzLogger,
                                                   parameters: RoleAppArguments,
                                                 ) extends RoleAppExecutor[F] {

  def runPlan(appPlan: AppStartupPlans): Unit = {
    injector.produce(appPlan.runtime).use {
      runtimeLocator =>
        val runner = runtimeLocator.get[DIEffectRunner[F]]

        runner.run {
          Injector.inherit(runtimeLocator).produceF[F](appPlan.integration).use {
            integrationLocator =>
              makeIntegrationCheck(lateLogger).check(appPlan.integrationKeys, integrationLocator)

              Injector.inherit(integrationLocator).produceF[F](appPlan.app).use {
                rolesLocator =>
                  val roleIndex = getRoleIndex(rolesLocator)

                  for {
                    _ <- runTasks(roleIndex)
                    _ <- runRoles(roleIndex)
                  } yield {

                  }
              }
          }
        }
    }
    hook.release()
  }

  protected def runRoles(index: Map[String, AbstractRoleF[F]]): F[Unit] = {
    val rolesToRun = parameters.roles.flatMap {
      r =>
        index.get(r.role) match {
          case Some(_: RoleTask2[F]) =>
            Seq.empty
          case Some(value: RoleService2[F]) =>
            Seq(value -> r)
          case Some(v) =>
            throw new DiAppBootstrapException(s"Inconsistent state: requested entrypoint ${r.role} has unexpected type: $v")
          case None =>
            throw new DiAppBootstrapException(s"Inconsistent state: requested entrypoint ${r.role} is missing")
        }
    }


    if (rolesToRun.nonEmpty) {
      lateLogger.info(s"Going to run: ${rolesToRun.size -> "roles"}")

      val tt = rolesToRun.map {
        case (task, cfg) =>
          task.start(cfg.roleParameters, cfg.freeArgs)
      }

      tt.foldLeft((_: Unit) => {
        hook.await(lateLogger)
      }) {
        case (acc, r) =>
          _ => r.use(acc)
      }(())
    } else {
      DIEffect[F].maybeSuspend(lateLogger.info("No services to run, exiting..."))
    }
  }


  protected def runTasks(index: Map[String, Object]): F[Unit] = {
    val tasksToRun = parameters.roles.flatMap {
      r =>
        index.get(r.role) match {
          case Some(value: RoleTask2[F]) =>
            Seq(value -> r)
          case Some(_: RoleService2[F]) =>
            Seq.empty
          case Some(v) =>
            throw new DiAppBootstrapException(s"Inconsistent state: requested entrypoint ${r.role} has unexpected type: $v")
          case None =>
            throw new DiAppBootstrapException(s"Inconsistent state: requested entrypoint ${r.role} is missing")
        }
    }

    lateLogger.info(s"Going to run: ${tasksToRun.size -> "tasks"}")

    DIEffect[F].traverse_(tasksToRun) {
      case (task, cfg) =>
        task.start(cfg.roleParameters, cfg.freeArgs)
    }
  }

  protected def makeIntegrationCheck(lateLogger: IzLogger): IntegrationChecker = {
    new IntegrationCheckerImpl(lateLogger)
  }

  private def getRoleIndex(rolesLocator: Locator): Map[String, AbstractRoleF[F]] = {
    roles.availableRoleBindings.map {
      b =>
        val key = DIKey.TypeKey(b.tpe)
        b.name -> (rolesLocator.index.get(key) match {
          case Some(value: AbstractRoleF[F]) =>
            value
          case o =>
            throw new DiAppBootstrapException(s"Requested $key for ${b.name}, unexpectedly got $o")
        })
    }.toMap
  }

}
