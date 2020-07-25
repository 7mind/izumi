package izumi.distage.testkit.services.dstest

import java.util.concurrent.{ConcurrentHashMap, TimeUnit}

import distage._
import izumi.distage.bootstrap.{BootstrapLocator, Cycles}
import izumi.distage.config.model.AppConfig
import izumi.distage.framework.model.exceptions.IntegrationCheckException
import izumi.distage.framework.model.{ActivationInfo, IntegrationCheck}
import izumi.distage.framework.services.{IntegrationChecker, PlanCircularDependencyCheck}
import izumi.distage.model.definition.Binding.SetElementBinding
import izumi.distage.model.definition.ImplDef
import izumi.distage.model.effect.DIEffect.syntax._
import izumi.distage.model.effect.{DIEffect, DIEffectAsync, DIEffectRunner}
import izumi.distage.model.exceptions.ProvisioningException
import izumi.distage.model.plan.{ExecutableOp, TriSplittedPlan}
import izumi.distage.roles.launcher.services.EarlyLoggers
import izumi.distage.testkit.DebugProperties
import izumi.distage.testkit.TestConfig.ParallelLevel
import izumi.distage.testkit.services.dstest.DistageTestRunner._
import izumi.distage.testkit.services.dstest.TestEnvironment.{EnvExecutionParams, MemoizationEnvWithPlan, PreparedTest}
import izumi.fundamentals.platform.cli.model.raw.RawAppArgs
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.integration.ResourceCheck
import izumi.fundamentals.platform.language.SourceFilePosition
import izumi.fundamentals.platform.strings.IzString._
import izumi.logstage.api.logger.LogRouter
import izumi.logstage.api.{IzLogger, Log}

import scala.concurrent.duration.{Duration, FiniteDuration}

class DistageTestRunner[F[_]: TagK](
  reporter: TestReporter,
  isTestSkipException: Throwable => Boolean,
) {
  def run(tests: Seq[DistageTest[F]]): Unit = {
    try {
      val envs = groupTests(tests)
      logEnvironmentsInfo(envs)
      groupAndSortByParallelLevel(envs)(_._1.envExec.parallelEnvs).foreach {
        case (level, parallelEnvs) => proceedEnvs(level)(parallelEnvs)
      }
    } catch {
      case t: Throwable =>
        reporter.onFailure(t)
    } finally {
      reporter.endAll()
    }
  }

  // first we need to plan runtime for our monad. Identity is also supported
  private[this] val runtimeGcRoots: Set[DIKey] = Set(
    DIKey.get[DIEffectRunner[F]],
    DIKey.get[DIEffect[F]],
    DIKey.get[DIEffectAsync[F]],
  )

  /**
    *  Performs tests grouping by it's memoization environment.
    *  [[TestEnvironment.EnvExecutionParams]] - contains only parts of environment that will not affect plan.
    *  Grouping by such structure will allow us to create memoization groups with shared logger and parallel execution policy.
    *  By result you'll got [[MemoizationEnvWithPlan]] mapped to tests wit it's plans.
    *  [[MemoizationEnvWithPlan]] represents memoization environment, with shared [[TriSplittedPlan]], [[Injector]], and runtime plan.
    */
  def groupTests(distageTests: Seq[DistageTest[F]]): Map[MemoizationEnvWithPlan, Seq[PreparedTest[F]]] = {

    // FIXME: _bootstrap_ keys that may vary between envs but shouldn't cause them to differ (because they should only impact bootstrap)
    val unstableKeys = {
      val activationKeys = Set(DIKey[Activation], DIKey[ActivationInfo])
      val recursiveKeys = Set(DIKey[BootstrapModule])
      // FIXME: remove PlanningObserverLoggingImpl and stop inserting LogstageModule in bootstrap
      val hackyKeys = Set(DIKey[LogRouter], DIKey[Activation]("initial"))
      activationKeys ++ recursiveKeys ++ hackyKeys
    }

    // here we are grouping our tests by memoization env

    distageTests.groupBy(_.environment.getExecParams).flatMap {
      case (envExec, grouped) =>
        val configLoadLogger = IzLogger(envExec.logLevel).withCustomContext("phase" -> "testRunner")
        val memoizationEnvs = grouped.groupBy(_.environment).flatMap {
          case (env, tests) =>
            withRecoverFromFailedExecution(tests) {
              prepareGroupPlans(unstableKeys, envExec, configLoadLogger, env, tests)
            }(None)
        }
        // merge environments together by equality of their shared & runtime plans
        // in a lot of cases memoization plan will be the same even with many minor changes to TestConfig,
        // so this saves a lot of realloaction of memoized resoorces
        val mergedEnvs = memoizationEnvs.groupBy(_.envMergeCriteria)

        mergedEnvs.map {
          case (EnvMergeCriteria(_, _, shared, runtimePlan, _), packedEnv) =>
            val integrationLogger = packedEnv.head.anyIntegrationLogger
            val memoizationInjector = packedEnv.head.anyMemoizationInjector
            val highestDebugOutputInTests = packedEnv.exists(_.highestDebugOutputInTests)
            val allStrengthenedKeys = packedEnv.iterator.flatMap(_.strengthenedKeys).toSet
            val tests = packedEnv.iterator.flatMap(_.testPlans).toSeq

            MemoizationEnvWithPlan(envExec, integrationLogger, shared, runtimePlan, memoizationInjector, highestDebugOutputInTests, allStrengthenedKeys) -> tests
        }
    }
  }

  private def prepareGroupPlans(
    unstableKeys: Set[DIKey],
    envExec: EnvExecutionParams,
    configLoadLogger: IzLogger,
    env: TestEnvironment,
    tests: Seq[DistageTest[F]],
  ): Option[PackedEnv[F]] = {
    // make a config loader for current env with logger
    val config = loadConfig(env, configLoadLogger)
    val lateLogger = EarlyLoggers.makeLateLogger(RawAppArgs.empty, configLoadLogger, config, envExec.logLevel, defaultLogFormatJson = false)

    // here we scan our classpath to enumerate of our components (we have "bootstrap" components - injector plugins, and app components)
    val options = envExec.planningOptions
    val provider = env.bootstrapFactory.makeModuleProvider[F](options, config, lateLogger.router, env.roles, env.activationInfo, env.activation)

    val bsModule = provider.bootstrapModules().merge overridenBy env.bsModule
    val finalBsModule = BootstrapLocator.defaultBootstrap overridenBy bsModule

    val appModule = provider.appModules().merge overridenBy env.appModule

    val (bsPlanMinusUnstable, bsModuleMinusUnstable, injector, planner) = {
      // FIXME: Including both bootstrap Plan & bootstrap Module into merge criteria to prevent `Bootloader`
      //  becoming becoming inconsistent across envs (if BootstrapModule isn't considered it could come from different env than expected).

      // FIXME: We're also removing & re-injecting Planner, Activations & BootstrapModule (in 0.11.0 activation won't be set via bsModules & won't be stored in Planner)
      //  (planner holds activations & the rest is for Bootloader self-introspection)

      // create bsLocator separately because we can't retrieve Plan & Module from Injector
      // fixme: adding Cycles.Proxy manually here
      val bsLocator = new BootstrapLocator(finalBsModule, Activation(Cycles -> Cycles.Proxy) ++ env.activation)

      val bsPlanMinusUnstable = bsLocator.plan.steps.filterNot(unstableKeys contains _.target)
      val bsModuleMinusUnstable = bsLocator.get[BootstrapModule].drop(unstableKeys)

      val injector = Injector.inherit(bsLocator)
      val planner = bsLocator.get[Planner]
      (bsPlanMinusUnstable, bsModuleMinusUnstable, injector, planner)
    }

    // runtime plan with `runtimeGcRoots`
    val runtimePlan = injector.plan(PlannerInput(appModule, env.activation, runtimeGcRoots))
    // all keys created in runtimePlan, we filter them out later to not recreate any components already in runtimeLocator
    val runtimeKeys = runtimePlan.keys

    // produce plan for each test
    val testPlans = tests.map {
      distageTest =>
        val testRoots = distageTest.test.get.diKeys.toSet ++ env.forcedRoots
        val plan = if (testRoots.nonEmpty) injector.plan(PlannerInput(appModule, env.activation, testRoots)) else OrderedPlan.empty
        PreparedTest(distageTest, plan, env.activationInfo, env.activation, planner)
    }
    val envKeys = testPlans.flatMap(_.testPlan.keys).toSet
    val sharedKeys = envKeys.intersect(env.memoizationRoots) -- runtimeKeys

    // we need to "strengthen" all _memoized_ weak set instances that occur in our tests to ensure that they
    // be created and persist in memoized set. we do not use strengthened bindings afterwards, so non-memoized
    // weak sets behave as usual
    val (strengthenedKeys, strengthenedAppModule) = appModule.drop(runtimeKeys).foldLeftWith(List.empty[DIKey]) {
      case (acc, b @ SetElementBinding(key, r: ImplDef.ReferenceImpl, _, _)) if r.weak && (envKeys(key) || envKeys(r.key)) =>
        (key :: acc) -> b.copy(implementation = r.copy(weak = false))
      case (acc, b) =>
        acc -> b
    }

    // compute [[TriSplittedPlan]] of our test, to extract shared plan, and perform it only once
    val shared = injector.trisectByKeys(env.activation, strengthenedAppModule, sharedKeys) {
      _.collectChildren[IntegrationCheck[F]].map(_.target).toSet
    }

    val envMergeCriteria = EnvMergeCriteria(bsPlanMinusUnstable.toSet, bsModuleMinusUnstable, shared, runtimePlan, envExec)

    val memoEnvHashCode = envMergeCriteria.hashCode()
    val integrationLogger = lateLogger("memoEnv" -> memoEnvHashCode)
    val highestDebugOutputInTests = tests.exists(_.environment.debugOutput)
    if (strengthenedKeys.nonEmpty) {
      integrationLogger.log(testkitDebugMessagesLogLevel(env.debugOutput))(
        s"Strengthened weak components: $strengthenedKeys"
      )
    }

    Option(PackedEnv(envMergeCriteria, testPlans, injector, integrationLogger, highestDebugOutputInTests, strengthenedKeys.toSet))
  }

  def proceedEnvs(parallel: ParallelLevel)(envs: Iterable[(MemoizationEnvWithPlan, Iterable[PreparedTest[F]])]): Unit = {
    configuredForeach(parallel)(envs) {
      case (MemoizationEnvWithPlan(envExec, integrationLogger, memoizationPlan, runtimePlan, memoizationInjector, _, allStrengthenedKeys), tests) =>
        val allEnvTests = tests.map(_.test)
        integrationLogger.info(s"Processing ${allEnvTests.size -> "tests"} using ${TagK[F].tag -> "monad"}")
        withRecoverFromFailedExecution_(allEnvTests) {
          val envIntegrationChecker = new IntegrationChecker.Impl[F](integrationLogger)
          val planChecker = new PlanCircularDependencyCheck(envExec.planningOptions, integrationLogger)

          // producing and verifying runtime plan
          assert(runtimeGcRoots.diff(runtimePlan.keys).isEmpty)
          planChecker.verify(runtimePlan)
          memoizationInjector.produceF[Identity](runtimePlan).use {
            runtimeLocator =>
              val runner = runtimeLocator.get[DIEffectRunner[F]]
              implicit val F: DIEffect[F] = runtimeLocator.get[DIEffect[F]]
              implicit val P: DIEffectAsync[F] = runtimeLocator.get[DIEffectAsync[F]]

              runner.run {
                withTestsRecoverCase(allEnvTests) {
                  // producing full runtime plan with all shared keys
                  withIntegrationSharedPlan(runtimeLocator, planChecker, envIntegrationChecker, memoizationPlan, allEnvTests) {
                    memoizedIntegrationLocator =>
                      proceedSuites(planChecker, memoizedIntegrationLocator, integrationLogger, allStrengthenedKeys)(tests)
                  }
                }
              }
          }
        }
    }
  }

  protected def withIntegrationSharedPlan(
    runtimeLocator: Locator,
    planCheck: PlanCircularDependencyCheck,
    checker: IntegrationChecker[F],
    plan: TriSplittedPlan,
    tests: => Iterable[DistageTest[F]],
  )(use: Locator => F[Unit]
  )(implicit
    F: DIEffect[F]
  ): F[Unit] = {
    // shared plan
    planCheck.verify(plan.shared)
    Injector.inherit(runtimeLocator).produceF[F](plan.shared).use {
      sharedLocator =>
        // integration plan
        planCheck.verify(plan.side)
        Injector.inherit(sharedLocator).produceF[F](plan.side).use {
          integrationSharedLocator =>
            withIntegrationCheck(checker, integrationSharedLocator)(tests, plan) {
              // main plan
              planCheck.verify(plan.primary)
              Injector.inherit(integrationSharedLocator).produceF[F](plan.primary).use {
                mainSharedLocator =>
                  use(mainSharedLocator)
              }
            }
        }
    }
  }

  protected def withTestsRecoverCase(tests: => Iterable[DistageTest[F]])(testsAction: => F[Unit])(implicit F: DIEffect[F]): F[Unit] = {
    F.definitelyRecoverCause {
      testsAction
    } {
      case (ProvisioningIntegrationException(integrations), _) =>
        // FIXME: temporary hack to allow missing containers to skip tests (happens when both DockerWrapper & integration check that depends on Docker.Container are memoized)
        F.maybeSuspend(ignoreIntegrationCheckFailedTests(tests, integrations))
      case (_, getTrace) =>
        // fail all tests (if an exception reached here, it must have happened before the individual test runs)
        F.maybeSuspend(failAllTests(tests, getTrace()))
    }
  }

  protected def withRecoverFromFailedExecution_[A](allTests: => Iterable[DistageTest[F]])(f: => Unit): Unit = {
    withRecoverFromFailedExecution(allTests)(f)(())
  }

  protected def withRecoverFromFailedExecution[A](allTests: => Iterable[DistageTest[F]])(f: => A)(onError: => A): A = {
    try {
      f
    } catch {
      case t: Throwable =>
        // fail all tests (if an exception reaches here, it must have happened before the runtime was successfully produced)
        failAllTests(allTests.toSeq, t)
        reporter.onFailure(t)
        onError
    }
  }

  protected def withIntegrationCheck(
    checker: IntegrationChecker[F],
    integrationLocator: Locator,
  )(tests: => Iterable[DistageTest[F]],
    plans: TriSplittedPlan,
  )(onSuccess: => F[Unit]
  )(implicit
    F: DIEffect[F]
  ): F[Unit] = {
    checker.collectFailures(plans.side.declaredRoots, integrationLocator).flatMap {
      case Left(failures) =>
        F.maybeSuspend {
          ignoreIntegrationCheckFailedTests(tests, failures)
        }
      case Right(_) =>
        onSuccess
    }
  }

  protected def ignoreIntegrationCheckFailedTests(tests: Iterable[DistageTest[F]], failures: Seq[ResourceCheck.Failure]): Unit = {
    tests.foreach {
      test =>
        reporter.testStatus(test.meta, TestStatus.Ignored(failures))
    }
  }

  protected def failAllTests(tests: Iterable[DistageTest[F]], t: Throwable): Unit = {
    tests.foreach {
      test =>
        reporter.testStatus(test.meta, TestStatus.Failed(t, Duration.Zero))
    }
  }

  protected def proceedSuites(
    planChecker: PlanCircularDependencyCheck,
    mainSharedLocator: Locator,
    testRunnerLogger: IzLogger,
    allStrengthenedKeys: Set[DIKey],
  )(testPlans: Iterable[PreparedTest[F]]
  )(implicit
    F: DIEffect[F],
    P: DIEffectAsync[F],
  ): F[Unit] = {
    val testsBySuite = testPlans.groupBy {
      case PreparedTest(test, _, _, _, _) =>
        val testId = test.meta.id
        SuiteData(testId.suiteName, testId.suiteId, testId.suiteClassName)
    }
    // now we are ready to run each individual test
    // note: scheduling here is custom also and tests may automatically run in parallel for any non-trivial monad
    // we assume that individual tests within a suite can't have different values of `parallelSuites`
    // (because of structure & that difference even if happens wouldn't be actionable at the level of suites anyway)
    groupedConfiguredTraverse_(testsBySuite)(_._2.head.test.environment.parallelSuites) {
      case (suiteData, preparedTests) =>
        F.bracket(
          acquire = F.maybeSuspend(reporter.beginSuite(suiteData))
        )(release = _ => F.maybeSuspend(reporter.endSuite(suiteData))) {
          _ =>
            groupedConfiguredTraverse_(preparedTests)(_.test.environment.parallelTests) {
              proceedTest(planChecker, mainSharedLocator, testRunnerLogger, allStrengthenedKeys)
            }
        }
    }
  }

  protected def proceedTest(
    planChecker: PlanCircularDependencyCheck,
    mainSharedLocator: Locator,
    testRunnerLogger: IzLogger,
    allStrengthenedKeys: Set[DIKey],
  )(preparedTest: PreparedTest[F]
  )(implicit F: DIEffect[F]
  ): F[Unit] = {
    val PreparedTest(test, testPlan, activationInfo, activation, planner) = preparedTest

    val locatorWithOverridenActivation: LocatorDef = new LocatorDef {
      // we override ActivationInfo & Activation because the test can have _different_ activation from the memoized part
      // FIXME: Activation will be part of PlannerInput in 0.11.0 & perhaps ActivationInfo should be derived from Bootloader/PlannerInput as well instead of injected externally
      make[ActivationInfo].fromValue(activationInfo)
      make[Activation].fromValue(activation)
      make[Activation].named("initial").fromValue(activation)
      make[Planner].fromValue(planner)
      make[BootstrapModule].fromValue(new BootstrapModuleDef {
        include(mainSharedLocator.get[BootstrapModule].drop {
          Set(
            DIKey[BootstrapModule],
            DIKey[ActivationInfo],
            DIKey[Activation],
            DIKey[Activation]("initial"),
          )
        })
        make[BootstrapModule].from(() => this)
        make[ActivationInfo].fromValue(activationInfo)
        make[Activation].fromValue(activation)
        make[Activation].named("initial").fromValue(activation)
      })
      override val parent: Option[Locator] = Some(mainSharedLocator)
    }

    val testInjector = Injector.inherit(locatorWithOverridenActivation)

    val allSharedKeys = mainSharedLocator.allInstances.map(_.key).toSet

    val testIntegrationCheckKeys = testPlan.collectChildren[IntegrationCheck[F]].map(_.target).toSet -- allSharedKeys

    val newAppModule = test.environment.appModule.drop(allSharedKeys)
    val moduleKeys = newAppModule.keys
    // there may be strengthened keys which did not get into shared context, so we need to manually declare them as roots
//    val newRoots = testPlan.keys -- allSharedKeys // ++ allStrengthenedKeys.intersect(moduleKeys)
    val newRoots = testPlan.keys -- allSharedKeys ++ allStrengthenedKeys.intersect(moduleKeys)
    val newTestPlan = testInjector.trisectByRoots(test.environment.activation, newAppModule, newRoots, testIntegrationCheckKeys)

    val testLogger = testRunnerLogger("testId" -> test.meta.id)
    testLogger.log(testkitDebugMessagesLogLevel(test.environment.debugOutput))(
      s"""Running test...
         |
         |Test pre-integration plan: ${newTestPlan.shared}
         |
         |Test integration plan: ${newTestPlan.side}
         |
         |Test primary plan: ${newTestPlan.primary}""".stripMargin
    )

    val testIntegrationChecker = new IntegrationChecker.Impl[F](testLogger)

    planChecker.verify(newTestPlan.shared)
    planChecker.verify(newTestPlan.side)
    planChecker.verify(newTestPlan.primary)

    // we are ready to run the test, finally
    testInjector.produceF[F](newTestPlan.shared).use {
      sharedLocator =>
        Injector.inherit(sharedLocator).produceF[F](newTestPlan.side).use {
          integrationLocator =>
            withIntegrationCheck(testIntegrationChecker, integrationLocator)(Seq(test), newTestPlan) {
              proceedIndividual(test, newTestPlan.primary, integrationLocator)
            }
        }
    }
  }

  protected def proceedIndividual(test: DistageTest[F], testPlan: OrderedPlan, parent: Locator)(implicit F: DIEffect[F]): F[Unit] = {
    def testDuration(before: Option[Long]): FiniteDuration = {
      before.fold(Duration.Zero) {
        before =>
          val after = System.nanoTime()
          FiniteDuration(after - before, TimeUnit.NANOSECONDS)
      }
    }

    def doRecover(before: Option[Long])(action: => F[Unit]): F[Unit] = {
      F.definitelyRecoverCause(action) {
        case (s, _) if isTestSkipException(s) =>
          F.maybeSuspend {
            reporter.testStatus(test.meta, TestStatus.Cancelled(s.getMessage, testDuration(before)))
          }
        case (_, getTrace) =>
          F.maybeSuspend {
            reporter.testStatus(test.meta, TestStatus.Failed(getTrace(), testDuration(before)))
          }
      }
    }

    doRecover(None) {
      Injector.inherit(parent).produceF[F](testPlan).use {
        testLocator =>
          F.suspendF {
            val before = System.nanoTime()
            reporter.testStatus(test.meta, TestStatus.Running)

            doRecover(Some(before)) {
              testLocator
                .run(test.test)
                .flatMap(_ => F.maybeSuspend(reporter.testStatus(test.meta, TestStatus.Succeed(testDuration(Some(before))))))
            }
          }
      }
    }
  }

  protected[this] def loadConfig(env: TestEnvironment, envLogger: IzLogger): AppConfig = {
    DistageTestRunner
      .memoizedConfig
      .computeIfAbsent(
        (env.configBaseName, env.bootstrapFactory, env.configOverrides),
        _ => {
          val configLoader = env
            .bootstrapFactory
            .makeConfigLoader(env.configBaseName, envLogger)
            .map {
              appConfig =>
                env.configOverrides match {
                  case Some(overrides) =>
                    AppConfig(overrides.config.withFallback(appConfig.config).resolve())
                  case None =>
                    appConfig
                }
            }
          configLoader.loadConfig()
        },
      )
  }

  protected def groupedConfiguredTraverse_[A](
    l: Iterable[A]
  )(getParallelismGroup: A => ParallelLevel
  )(f: A => F[Unit]
  )(implicit
    F: DIEffect[F],
    P: DIEffectAsync[F],
  ): F[Unit] = {
    F.traverse_(groupAndSortByParallelLevel(l)(getParallelismGroup)) {
      case (level, l) => configuredTraverse_(level)(l)(f)
    }
  }

  private[this] def groupAndSortByParallelLevel[A](l: Iterable[A])(getParallelismGroup: A => ParallelLevel): List[(ParallelLevel, Iterable[A])] = {
    l.groupBy(getParallelismGroup).toList.sortBy {
      case (ParallelLevel.Unlimited, _) => 1
      case (ParallelLevel.Fixed(_), _) => 2
      case (ParallelLevel.Sequential, _) => 3
    }
  }

  protected def configuredTraverse_[A](parallel: ParallelLevel)(l: Iterable[A])(f: A => F[Unit])(implicit F: DIEffect[F], P: DIEffectAsync[F]): F[Unit] = {
    parallel match {
      case ParallelLevel.Fixed(n) if l.size > 1 => P.parTraverseN_(n)(l)(f)
      case ParallelLevel.Unlimited if l.size > 1 => P.parTraverse_(l)(f)
      case _ => F.traverse_(l)(f)
    }
  }

  protected def configuredForeach[A](parallel: ParallelLevel)(environments: Iterable[A])(f: A => Unit): Unit = {
    parallel match {
      case ParallelLevel.Fixed(n) if environments.size > 1 => DIEffectAsync.diEffectParIdentity.parTraverseN_(n)(environments)(f)
      case ParallelLevel.Unlimited if environments.size > 1 => DIEffectAsync.diEffectParIdentity.parTraverse_(environments)(f)
      case _ => environments.foreach(f)
    }
  }

  private[this] def logEnvironmentsInfo(envs: Map[MemoizationEnvWithPlan, Iterable[PreparedTest[F]]]): Unit = {
    val testRunnerLogger = {
      val minimumLogLevel = envs.map(_._1.envExec.logLevel).toSeq.sorted.headOption.getOrElse(Log.Level.Info)
      IzLogger(minimumLogLevel)("phase" -> "testRunner")
    }
    val originalEnvSize = envs.iterator.flatMap(_._2.map(_.test.environment)).toSet.size
    val memoEnvsSize = envs.size

    testRunnerLogger.info(s"Created ${memoEnvsSize -> "memoization envs"} with ${envs.iterator.flatMap(_._2).size -> "tests"} using ${TagK[F].tag -> "monad"}")
    if (originalEnvSize != memoEnvsSize) {
      testRunnerLogger.info(s"Merged together ${(originalEnvSize - memoEnvsSize) -> "raw environments"}")
    }

    envs.foreach {
      case (MemoizationEnvWithPlan(_, testEnvLogger, memoizationPlan, runtimePlan, _, debugOutput, _), tests) =>
        val suites = tests.map(_.test.meta.id.suiteClassName).toList.distinct
        testEnvLogger.info(s"Memoization environment with ${suites.size -> "suites"} ${tests.size -> "tests"} ${suites.sorted.niceList() -> "testSuites"}")
        testEnvLogger.log(testkitDebugMessagesLogLevel(debugOutput))(
          s"""Effect runtime plan: $runtimePlan
             |
             |Memoized pre-integration plan: ${memoizationPlan.shared}
             |
             |Memoized integration plan: ${memoizationPlan.side}
             |
             |Memoized primary plan: ${memoizationPlan.primary}""".stripMargin
        )
    }
  }

  private[this] def testkitDebugMessagesLogLevel(forceDebugOutput: Boolean): Log.Level = {
    if (DistageTestRunner.enableDebugOutput || forceDebugOutput) {
      Log.Level.Info
    } else {
      Log.Level.Debug
    }
  }

}

object DistageTestRunner {
  final case class EnvMergeCriteria(
    bsPlanMinusActivations: Set[ExecutableOp],
    bsModuleMinusActivations: BootstrapModule,
    sharedPlan: TriSplittedPlan,
    runtimePlan: OrderedPlan,
    envExec: EnvExecutionParams,
  )
  final case class PackedEnv[F[_]](
    envMergeCriteria: EnvMergeCriteria,
    testPlans: Seq[PreparedTest[F]],
    anyMemoizationInjector: Injector,
    anyIntegrationLogger: IzLogger,
    highestDebugOutputInTests: Boolean,
    strengthenedKeys: Set[DIKey],
  )

  final case class TestId(name: String, suiteName: String, suiteId: String, suiteClassName: String)
  final case class DistageTest[F[_]](test: Functoid[F[_]], environment: TestEnvironment, meta: TestMeta)
  final case class TestMeta(id: TestId, pos: SourceFilePosition, uid: Long)
  final case class SuiteData(suiteName: String, suiteId: String, suiteClassName: String)

  sealed trait TestStatus
  object TestStatus {
    case object Running extends TestStatus

    sealed trait Done extends TestStatus
    final case class Ignored(checks: Seq[ResourceCheck.Failure]) extends Done

    sealed trait Finished extends Done
    final case class Cancelled(clue: String, duration: FiniteDuration) extends Finished
    final case class Succeed(duration: FiniteDuration) extends Finished
    final case class Failed(t: Throwable, duration: FiniteDuration) extends Finished
  }

  trait TestReporter {
    def onFailure(f: Throwable): Unit
    def endAll(): Unit
    def beginSuite(id: SuiteData): Unit
    def endSuite(id: SuiteData): Unit
    def testStatus(test: TestMeta, testStatus: TestStatus): Unit
  }

  object ProvisioningIntegrationException {
    def unapply(arg: ProvisioningException): Option[Seq[ResourceCheck.Failure]] = {
      Some(arg.getSuppressed.collect { case i: IntegrationCheckException => i.failures }.toSeq)
        .filter(_.nonEmpty)
        .map(_.flatten)
    }
  }

  private val enableDebugOutput: Boolean = DebugProperties.`izumi.distage.testkit.debug`.boolValue(false)

  private final val memoizedConfig = new ConcurrentHashMap[(String, BootstrapFactory, Option[AppConfig]), AppConfig]

}
