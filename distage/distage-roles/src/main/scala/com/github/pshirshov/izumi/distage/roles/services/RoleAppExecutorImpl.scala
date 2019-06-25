package com.github.pshirshov.izumi.distage.roles.services

import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect.syntax._
import com.github.pshirshov.izumi.distage.roles._
import com.github.pshirshov.izumi.distage.roles.model.meta.RolesInfo
import com.github.pshirshov.izumi.distage.roles.model.{AbstractRoleF, DiAppBootstrapException, RoleService, RoleTask}
import com.github.pshirshov.izumi.distage.roles.services.IntegrationChecker.IntegrationCheckException
import com.github.pshirshov.izumi.distage.roles.services.RoleAppPlanner.AppStartupPlans
import com.github.pshirshov.izumi.distage.roles.services.StartupPlanExecutor.Filters
import com.github.pshirshov.izumi.fundamentals.platform.cli.model.raw.RawAppArgs
import com.github.pshirshov.izumi.logstage.api.IzLogger
import distage.{Injector, TagK}

class RoleAppExecutorImpl[F[_] : TagK](
                                        protected val hook: AppShutdownStrategy[F],
                                        roles: RolesInfo,
                                        injector: Injector,
                                        lateLogger: IzLogger,
                                        parameters: RawAppArgs,
                                      ) extends RoleAppExecutor[F] {

  final def runPlan(appPlan: AppStartupPlans): Unit = {
    try {
      makeStartupExecutor().execute(appPlan, Filters.all[F])(doRun)
    } finally {
      hook.release()
    }
  }

  protected def makeStartupExecutor(): StartupPlanExecutor = {
    StartupPlanExecutor.default(lateLogger, injector)
  }

  protected def doRun(locator: Locator, integrationCheckResult: Option[IntegrationCheckException], effect: DIEffect[F]): F[Unit] = {
    implicit val F: DIEffect[F] = effect
    for {
      _ <- integrationCheckResult.fold(F.unit)(F.fail(_))
      roleIndex = getRoleIndex(locator)
      _ <- runTasks(roleIndex)
      _ <- runRoles(roleIndex)
    } yield ()
  }

  protected def runRoles(index: Map[String, AbstractRoleF[F]])(implicit effect: DIEffect[F]): F[Unit] = {
    val rolesToRun = parameters.roles.flatMap {
      r =>
        index.get(r.role) match {
          case Some(_: RoleTask[F]) =>
            Seq.empty
          case Some(value: RoleService[F]) =>
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


  protected def runTasks(index: Map[String, Object])(implicit effect: DIEffect[F]): F[Unit] = {
    val tasksToRun = parameters.roles.flatMap {
      r =>
        index.get(r.role) match {
          case Some(value: RoleTask[F]) =>
            Seq(value -> r)
          case Some(_: RoleService[F]) =>
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

  private def getRoleIndex(rolesLocator: Locator): Map[String, AbstractRoleF[F]] = {
    roles.availableRoleBindings.flatMap {
      b =>
        rolesLocator.index.get(b.binding.key) match {
          case Some(value: AbstractRoleF[F]) =>
            Seq(b.descriptor.id -> value)
          case _ =>
            Seq.empty
        }
    }.toMap
  }

}
