package izumi.distage.framework.services

import izumi.distage.config.AppConfigModule
import izumi.distage.config.model.AppConfig
import izumi.distage.framework.config.PlanningOptions
import izumi.distage.framework.model.ActivationInfo
import izumi.distage.framework.services.ResourceRewriter.RewriteRules
import izumi.distage.model.definition.{BootstrapModule, BootstrapModuleDef, Id, Module, ModuleDef}
import izumi.distage.model.planning.PlanningHook
import izumi.distage.model.recursive.LocatorRef
import izumi.distage.planning.extensions.GraphDumpBootstrapModule
import izumi.distage.roles.launcher.{AppShutdownInitiator, AppShutdownStrategy}
import izumi.distage.roles.model.meta.RolesInfo
import izumi.functional.bio.Exit
import izumi.functional.bio.UnsafeRun2.FailureHandler
import izumi.fundamentals.platform.cli.model.raw.RawAppArgs
import izumi.logstage.api.IzLogger
import izumi.logstage.api.logger.LogRouter
import izumi.logstage.distage.{LogIOModule, LogstageModule}
import izumi.reflect.TagK

/**
  * This component is responsible for passing-through selected components from the outer [[izumi.distage.roles.RoleAppBootModule]]
  * context into DI scope of the started application.
  *
  * The application doesn't outright [[distage.Injector.inherit inherit]] the outer context because that would
  * bring in way too many unrelated components into scope.
  *
  * This will also add some other useful components:
  *
  *   - GraphViz dump hook will be enabled if [[PlanningOptions#addGraphVizDump]] is enabled (via `--debug-dump-graph` commandline parameter)
  *   - `IzLogger` will be passed in from the outer context
  *   - `LogIO[F]` will be available with the application's effect type
  *   - `LocatorRef @Id("roleapp")` allows accessing components from outer context if needed
  *
  * @see [[https://izumi.7mind.io/distage/debugging#graphviz-rendering GraphViz Rendering]]
  */
trait ModuleProvider { self =>
  def bootstrapModules(): Seq[BootstrapModule]
  def appModules(): Seq[Module]

  final def mapBootstrap(f: Seq[BootstrapModule] => Seq[BootstrapModule]): ModuleProvider = new ModuleProvider {
    override def bootstrapModules(): Seq[BootstrapModule] = f(self.bootstrapModules())
    override def appModules(): Seq[Module] = self.appModules()
  }
  final def mapApp(f: Seq[Module] => Seq[Module]): ModuleProvider = new ModuleProvider {
    override def bootstrapModules(): Seq[BootstrapModule] = self.bootstrapModules()
    override def appModules(): Seq[Module] = f(self.appModules())
  }
}

object ModuleProvider {

  class Impl[F[_]: TagK](
    logRouter: LogRouter,
    options: PlanningOptions,
    // pass-through
    config: AppConfig,
    roles: RolesInfo,
    args: RawAppArgs,
    activationInfo: ActivationInfo,
    shutdownStrategy: AppShutdownStrategy[F],
    roleAppLocator: Option[LocatorRef] @Id("roleapp"),
  ) extends ModuleProvider {

    def bootstrapModules(): Seq[BootstrapModule] = {
      val roleInfoModule = new BootstrapModuleDef {
        make[RolesInfo].fromValue(roles)
        make[RawAppArgs].fromValue(args)
        make[ActivationInfo].fromValue(activationInfo)
        make[AppShutdownInitiator[F]].fromValue(shutdownStrategy)
      }

      val loggerModule = new LogstageModule(logRouter, true)

      val resourceRewriter = new BootstrapModuleDef {
        make[RewriteRules]
          .fromValue(options.rewriteRules)
        many[PlanningHook]
          .add[ResourceRewriter]
      }

      val graphvizDumpModule = if (options.addGraphVizDump) new GraphDumpBootstrapModule() else BootstrapModule.empty

      val appConfigModule: BootstrapModule = AppConfigModule(config).morph[BootstrapModule]

      Seq(
        roleInfoModule,
        resourceRewriter,
        loggerModule,
        graphvizDumpModule,
        appConfigModule, // make config available for bootstrap plugins
      )
    }

    def appModules(): Seq[Module] = {
      Seq(
        LogIOModule[F](), // reuse IzLogger from BootstrapModule
        LogstageFailureHandlerModule,
      ) ++ roleAppLocator.map {
        outerLocator =>
          new ModuleDef {
            make[LocatorRef].named("roleapp").fromValue(outerLocator)
            make[RoleAppPlanner].from((_: LocatorRef @Id("roleapp")).get.get[RoleAppPlanner])
          }
      }
    }

  }

  object LogstageFailureHandlerModule extends ModuleDef {
    make[FailureHandler].from {
      logger: IzLogger =>
        FailureHandler.Custom {
          case Exit.Error(error, trace) =>
            logger.warn(s"Fiber errored out due to unhandled $error $trace")
          case Exit.Interruption(interrupt, trace) =>
            logger.trace(s"Fiber interrupted with $interrupt $trace")
          case Exit.Termination(defect, _, trace) =>
            logger.warn(s"Fiber terminated erroneously with unhandled $defect $trace")
        }
    }
  }

}
