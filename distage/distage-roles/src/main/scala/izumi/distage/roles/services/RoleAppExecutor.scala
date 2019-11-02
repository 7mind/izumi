package izumi.distage.roles.services

import distage.{Injector, TagK}
import izumi.distage.model.Locator
import izumi.distage.model.monadic.DIEffect
import izumi.distage.model.monadic.DIEffect.syntax._
import izumi.distage.roles._
import izumi.distage.roles.model.meta.RolesInfo
import izumi.distage.roles.model.{AbstractRoleF, DiAppBootstrapException, RoleService, RoleTask}
import izumi.distage.roles.services.RoleAppPlanner.AppStartupPlans
import izumi.distage.roles.services.StartupPlanExecutor.Filters
import izumi.fundamentals.platform.cli.model.raw.RawAppArgs
import izumi.logstage.api.IzLogger

trait RoleAppExecutor[F[_]] {
  def runPlan(appPlan: AppStartupPlans): Unit
}

object RoleAppExecutor {

  class Impl[F[_] : TagK](
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

    protected def doRun(locator: Locator, effect: DIEffect[F]): F[Unit] = {
      val roleIndex = getRoleIndex(locator)
      implicit val e: DIEffect[F] = effect
      for {
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

        val finalizer = (_: Unit) => {
          hook.await(lateLogger)
        }
        val f = tt.foldLeft(finalizer) {
          case (acc, role) =>
            _ =>
              val loggedTask = for {
                _ <- effect.maybeSuspend(lateLogger.info(s"Role is about to initialize: $role"))
                _ <- role.use(acc)
                _ <- effect.maybeSuspend(lateLogger.info(s"Role initialized: $role"))
              } yield ()

              effect.definitelyRecover(loggedTask) {
                t =>
                  for {
                    _ <- effect.maybeSuspend(lateLogger.error(s"Role $role failed: $t"))
                    _ <- effect.fail[Unit](t)
                  } yield ()
              }
        }
        f(())
      } else {
        effect.maybeSuspend(lateLogger.info("No services to run, exiting..."))
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

      effect.traverse_(tasksToRun) {
        case (task, cfg) =>
          val loggedTask = for {
            _ <- effect.maybeSuspend(lateLogger.info(s"Task is about to start: $task"))
            _ <- task.start(cfg.roleParameters, cfg.freeArgs)
            _ <- effect.maybeSuspend(lateLogger.info(s"Task finished: $task"))
          } yield ()

          effect.definitelyRecover(loggedTask) {
            error =>
              for {
                _ <- effect.maybeSuspend(lateLogger.error(s"Task failed: $task, $error"))
                _ <- effect.fail[Unit](error)
              } yield ()
          }
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

}
