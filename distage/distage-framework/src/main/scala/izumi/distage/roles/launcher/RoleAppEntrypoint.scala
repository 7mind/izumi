package izumi.distage.roles.launcher

import izumi.distage.model.Locator
import izumi.distage.model.definition.Lifecycle
import izumi.distage.roles.model.exceptions.DIAppBootstrapException
import izumi.distage.roles.model.meta.RolesInfo
import izumi.distage.roles.model.{AbstractRole, RoleService, RoleTask}
import izumi.functional.bio.{Async2, IO2, Primitives2}
import izumi.fundamentals.platform.cli.model.RoleAppArgs
import izumi.logstage.api.IzLogger
import izumi.reflect.TagKK

trait RoleAppEntrypoint[F[+_, +_]] {
  def runTasksAndRoles(locator: Locator, effect: IO2[F], effectAsync: Async2[F]): F[Throwable, Unit]
}

object RoleAppEntrypoint {
  class Impl[F[+_, +_]: TagKK: Primitives2](
    roles: RolesInfo,
    lateLogger: IzLogger,
    parameters: RoleAppArgs,
    hook: AppShutdownStrategy[F],
  ) extends RoleAppEntrypoint[F] {

    override def runTasksAndRoles(locator: Locator, effect: IO2[F], effectAsync: Async2[F]): F[Throwable, Unit] = {
      implicit val F: IO2[F] = effect
      val roleIndex = getRoleIndex(locator)
      for {
        _ <- runTasks(roleIndex)
        _ <- runRoles(roleIndex)(using F, effectAsync)
      } yield ()
    }

    protected def runRoles(index: Map[String, AbstractRole[F]])(implicit F: IO2[F], FA: Async2[F]): F[Throwable, Unit] = {
      val rolesToRun = parameters.roles.flatMap {
        r =>
          index.get(r.role) match {
            case Some(_: RoleTask[F]) =>
              Seq.empty
            case Some(value: RoleService[F]) =>
              Seq(value -> r)
            case None =>
              throw new DIAppBootstrapException(s"Inconsistent state: requested entrypoint ${r.role} is missing")
          }
      }

      if (rolesToRun.nonEmpty) {
        lateLogger.info(s"Going to run: ${rolesToRun.size -> "roles"}")

        val roleServices = rolesToRun.map {
          case (task, cfg) =>
            task -> task.start(cfg.roleParameters)
        }

        Lifecycle
          .traverse(roleServices) {
            case (role, resource) =>
              resource
                .wrapAcquire {
                  acquire =>
                    F.suspendThrowable {
                      lateLogger.info(s"Role is about to initialize: $role")
                      acquire.flatMap(a => F.sync { lateLogger.info(s"Role initialized: $role"); a })
                    }
                }.catchAll {
                  t =>
                    Lifecycle.liftF {
                      F.suspendThrowable {
                        lateLogger.error(s"Role $role failed: $t")
                        F.fail(t)
                      }
                    }
                }
          }
          .use(_ => hook.awaitShutdown(lateLogger))
      } else {
        F.sync(lateLogger.info("No services to run, exiting..."))
      }
    }

    protected def runTasks(index: Map[String, AbstractRole[F]])(implicit F: IO2[F]): F[Throwable, Unit] = {
      val tasksToRun = parameters.roles.flatMap {
        r =>
          index.get(r.role) match {
            case Some(value: RoleTask[F]) =>
              Seq(value -> r)
            case Some(_: RoleService[F]) =>
              Seq.empty
            case None =>
              throw new DIAppBootstrapException(s"Inconsistent state: requested entrypoint ${r.role} is missing")
          }
      }

      lateLogger.info(s"Going to run: ${tasksToRun.size -> "tasks"}")

      F.traverse_(tasksToRun) {
        case (task, cfg) =>
          val loggedTask: F[Throwable, Unit] = for {
            _ <- F.sync(lateLogger.info(s"Task is about to start: $task"))
            _ <- task.start(cfg.roleParameters)
            _ <- F.sync(lateLogger.info(s"Task finished: $task"))
          } yield ()

          // Sandbox captures both typed Throwable failures and defects (panics) as
          // `Exit.FailureUninterrupted[Throwable]`; we log and re-raise via the typed channel.
          F.sandboxCatchAll[Throwable, Unit, Throwable](loggedTask) {
            exit =>
              val error = exit.toThrowable
              for {
                _ <- F.sync(lateLogger.error(s"Task failed: $task, $error, $exit"))
                _ <- F.fail(error): F[Throwable, Unit]
              } yield ()
          }
      }
    }

    private def getRoleIndex(rolesLocator: Locator): Map[String, AbstractRole[F]] = {
      roles.availableRoleBindings.flatMap {
        b =>
          rolesLocator.lookupInstance[AbstractRole[F]](b.binding.key) match {
            case Some(value) =>
              Seq(b.id -> value)
            case _ =>
              Seq.empty
          }
      }.toMap
    }

  }
}
