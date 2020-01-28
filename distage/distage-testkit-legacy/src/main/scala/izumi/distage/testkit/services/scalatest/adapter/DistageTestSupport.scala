package izumi.distage.testkit.services.scalatest.adapter

import distage.config.AppConfig
import distage.{DIKey, Injector, ModuleBase}
import izumi.distage.framework.config.PlanningOptions
import izumi.distage.framework.model.ActivationInfo
import izumi.distage.framework.model.exceptions.IntegrationCheckException
import izumi.distage.framework.services.{ConfigLoader, IntegrationChecker, ModuleProvider, RoleAppPlanner}
import izumi.distage.model.{Locator, PlannerInput}
import izumi.distage.model.definition.Binding.SingletonBinding
import izumi.distage.model.definition.{Activation, Binding, BootstrapModule, ImplDef, Module}
import izumi.distage.model.effect.DIEffect
import izumi.distage.model.effect.DIEffect.syntax._
import izumi.distage.model.providers.ProviderMagnet
import izumi.distage.model.recursive.Bootloader
import izumi.distage.roles.model.meta.RolesInfo
import izumi.distage.roles.services.StartupPlanExecutor.Filters
import izumi.distage.roles.services._
import izumi.distage.testkit.services.dstest.TestEnvironment
import izumi.distage.testkit.services.scalatest.adapter.ExternalResourceProvider.{MemoizedInstance, OrderedFinalizer, PreparedShutdownRuntime}
import izumi.fundamentals.platform.cli.model.raw.RawAppArgs
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.{CodePosition, unused}
import izumi.fundamentals.reflection.Tags.TagK
import izumi.logstage.api.IzLogger
import izumi.logstage.api.Log.Level

@deprecated("Use dstest", "2019/Jul/18")
abstract class DistageTestSupport[F[_]](implicit val tagK: TagK[F])
  extends DISyntax[F]
    with IgnoreSupport
    with SuppressionSupport {

  private lazy val erpInstance = externalResourceProvider

  protected def externalResourceProvider: ExternalResourceProvider = ExternalResourceProvider.Null

  protected def memoizationContextId: MemoizationContextId

  protected final def doMemoize(locator: Locator): Unit = {
    val fmap = locator.finalizers[F].zipWithIndex.map {
      case (f, idx) =>
        f.key -> OrderedFinalizer(f, idx)
    }.toMap
    locator
      .allInstances
      .foreach {
        ref =>
          externalResourceProvider.process(memoizationContextId, MemoizedInstance[Any](ref, fmap.get(ref.key)))
      }
  }

  override protected def takeIO(function: ProviderMagnet[F[_]], @unused pos: CodePosition): Unit = {
    verifyTotalSuppression()

    val logger = makeLogger()
    val loader = makeConfigLoader(logger)
    val env = loadEnvironment(logger)
    val config = loader.buildConfig()
    val options = contextOptions()
    val provider = makeModuleProvider(options, config, logger, env.roles, env.activationInfo, env.activation)

    val bsModule = provider.bootstrapModules().merge overridenBy env.bsModule overridenBy bootstrapOverride
    val appModule = provider.appModules().merge overridenBy env.appModule

    val allRoots = function.get.diKeys.toSet ++ additionalRoots

    val refinedBindings = refineBindings(allRoots, appModule)
    val withMemoized = applyMemoization(refinedBindings)
    val planner = makePlanner(options, bsModule, logger, Injector.bootloader(PlannerInput(withMemoized overridenBy appOverride, allRoots)))

    val plan = planner.makePlan(allRoots)

    erpInstance.registerShutdownRuntime[F](PreparedShutdownRuntime[F](
      plan.injector.produceF[Identity](plan.runtime)
    ))

    val filters = Filters[F](
      _.filterNot(f => erpInstance.isMemoized(memoizationContextId, f.key)),
      _.filterNot(f => erpInstance.isMemoized(memoizationContextId, f.key)),
    )

    verifyTotalSuppression()
    try {
      makeExecutor(plan.injector, logger)
        .execute(plan, filters) {
          (locator, effect) =>
            implicit val F: DIEffect[F] = effect

            for {
              _ <- F.maybeSuspend(doMemoize(locator))
              _ <- F.maybeSuspend(verifyTotalSuppression())
              _ <- F.maybeSuspend(beforeRun(locator))
              _ <- F.maybeSuspend(verifyTotalSuppression())
              _ <- locator.run(function)
            } yield ()
        }
    } catch {
      case i: IntegrationCheckException =>
        suppressTheRestOfTestSuite()
        ignoreThisTest(Some(i.getMessage), Option(i.getCause))
    } finally {
      val cacheSize = erpInstance.size(memoizationContextId)
      if (cacheSize > 0) {
        logger.info(s"${cacheSize -> "memoized instances"} in ${memoizationContextId -> "memoization context"}")
      }
    }
  }

  protected def bootstrapOverride: BootstrapModule = BootstrapModule.empty

  protected def appOverride: ModuleBase = Module.empty

  protected final def applyMemoization(refinedBindings: ModuleBase): ModuleBase = {
    refinedBindings.map {
      b =>
        erpInstance.getMemoized(memoizationContextId, b.key) match {
          case Some(value) =>
            val impltype = b match {
              case binding: Binding.ImplBinding =>
                binding.implementation.implType
              case binding: Binding.SetBinding =>
                binding match {
                  case e: Binding.SetElementBinding =>
                    e.implementation.implType
                  case s: Binding.EmptySetBinding[_] =>
                    s.key.tpe
                }
            }
            val impl = ImplDef.InstanceImpl(impltype, value)
            SingletonBinding(b.key, impl, b.tags, b.origin)

          case None =>
            b
        }

    }
  }

  protected def loadEnvironment(logger: IzLogger): TestEnvironment

  protected def bootstrapLogLevel: Level = IzLogger.Level.Warn

  protected def additionalRoots: Set[DIKey] = Set.empty

  protected def makeLogger(): IzLogger = IzLogger(bootstrapLogLevel)("phase" -> "test")

  protected def makeModuleProvider(options: PlanningOptions, config: AppConfig, lateLogger: IzLogger, roles: RolesInfo, activationInfo: ActivationInfo, activation: Activation): ModuleProvider = {
    // roles descriptor is not actually required there, we bind it just in case someone wish to inject a class depending on it
    new ModuleProvider.Impl[F](
      logger = lateLogger,
      config = config,
      roles = roles,
      options = options,
      args = RawAppArgs.empty,
      activationInfo = activationInfo,
      activation = activation,
    )
  }

  protected def contextOptions(): PlanningOptions = PlanningOptions()

  protected def makePlanner(options: PlanningOptions, bsModule: BootstrapModule, logger: IzLogger, reboot: Bootloader): RoleAppPlanner[F] = {
    new RoleAppPlanner.Impl[F](options, bsModule, logger, reboot)
  }

  protected def makeExecutor(injector: Injector, logger: IzLogger): StartupPlanExecutor[F] = {
    StartupPlanExecutor(injector, new IntegrationChecker.Impl[F](logger))
  }

  /** You can override this to e.g. skip test when certain external dependencies are not available **/
  protected def beforeRun(@unused context: Locator): Unit = {}

  protected def makeConfigLoader(logger: IzLogger): ConfigLoader = {
    val thisClass = this.getClass
    val pname = s"${thisClass.getPackage.getName}"
    val lastPackage = pname.split('.').last
    val classname = thisClass.getName

    val moreConfigs = Map(
      s"$lastPackage-test" -> None,
      s"$classname-test" -> None,
    )
    new ConfigLoader.LocalFSImpl(logger, None, moreConfigs)
  }

  protected def refineBindings(@unused roots: Set[DIKey], primaryModule: ModuleBase): ModuleBase = primaryModule
}
