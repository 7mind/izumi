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
import izumi.distage.modules.DefaultModule
import izumi.distage.modules.support.IdentitySupportModule
import izumi.distage.roles.launcher.EarlyLoggers
import izumi.distage.testkit.DebugProperties
import izumi.distage.testkit.TestConfig.ParallelLevel
import izumi.distage.testkit.services.dstest.DistageTestRunner._
import izumi.distage.testkit.services.dstest.TestEnvironment.{EnvExecutionParams, MemoizationEnv, PreparedTest}
import izumi.fundamentals.collections.nonempty.NonEmptyList
import izumi.fundamentals.platform.cli.model.raw.RawAppArgs
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.integration.ResourceCheck
import izumi.fundamentals.platform.language.SourceFilePosition
import izumi.logstage.api.logger.LogRouter
import izumi.logstage.api.{IzLogger, Log}

import scala.annotation.tailrec
import scala.collection.concurrent.TrieMap
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.{Duration, FiniteDuration}

class DistageTestRunner[F[_]: TagK: DefaultModule](
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
    *  By result you'll got [[MemoizationEnv]] mapped to tests with it's plans.
    *  [[MemoizationEnv]] represents memoization environment, with shared [[TriSplittedPlan]], [[Injector]], and runtime plan.
    */
  def groupTests(distageTests: Seq[DistageTest[F]]): Map[MemoizationEnv, MemoizationTree[F]] = {

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
              Option(prepareGroupPlans(unstableKeys, envExec, configLoadLogger, env, tests))
            }(None)
        }
        // merge environments together by equality of their shared & runtime plans
        // in a lot of cases memoization plan will be the same even with many minor changes to TestConfig,
        // so this saves a lot of realloaction of memoized resources
        val mergedEnvs = memoizationEnvs.groupBy(_.envMergeCriteria)

        mergedEnvs.map {
          case (EnvMergeCriteria(_, _, runtimePlan, _), packedEnv) =>
            val integrationLogger = packedEnv.head.anyIntegrationLogger
            val memoizationInjector = packedEnv.head.anyMemoizationInjector
            val highestDebugOutputInTests = packedEnv.exists(_.highestDebugOutputInTests)
            val allStrengthenedKeys = packedEnv.iterator.flatMap(_.strengthenedKeys).toSet
            val memoizationTree = MemoizationTree[F](packedEnv)
            MemoizationEnv(envExec, integrationLogger, runtimePlan, memoizationInjector, highestDebugOutputInTests, allStrengthenedKeys) -> memoizationTree
        }
    }
  }
  private[this] def prepareSharedPlan(
    envKeys: Set[DIKey],
    runtimeKeys: Set[DIKey],
    memoizationRoots: Set[DIKey],
    activation: Activation,
    injector: Injector[Identity],
    appModule: Module,
  ): TriSplittedPlan = {
    val sharedKeys = envKeys.intersect(memoizationRoots) -- runtimeKeys
    // compute [[TriSplittedPlan]] of our test, to extract shared plan, and perform it only once
    injector.trisectByKeys(activation, appModule, sharedKeys) {
      _.collectChildrenKeysSplit[IntegrationCheck[Identity], IntegrationCheck[F]]
    }
  }

  private def prepareGroupPlans(
    unstableKeys: Set[DIKey],
    envExec: EnvExecutionParams,
    configLoadLogger: IzLogger,
    env: TestEnvironment,
    tests: Seq[DistageTest[F]],
  ): PackedEnv[F] = {
    // make a config loader for current env with logger
    val config = loadConfig(env, configLoadLogger)
    val lateLogger = EarlyLoggers.makeLateLogger(RawAppArgs.empty, configLoadLogger, config, envExec.logLevel, defaultLogFormatJson = false)

    // here we scan our classpath to enumerate of our components (we have "bootstrap" components - injector plugins, and app components)
    val provider = env.bootstrapFactory.makeModuleProvider[F](envExec.planningOptions, config, lateLogger.router, env.roles, env.activationInfo, env.activation)

    val finalBsModule = {
      val bsModule = provider.bootstrapModules().merge overridenBy env.bsModule
      BootstrapLocator.defaultBootstrap overridenBy bsModule
    }

    val appModule = IdentitySupportModule ++ DefaultModule[F] overridenBy provider.appModules().merge overridenBy env.appModule

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
        val forcedRoots = env.forcedRoots.getActiveKeys(env.activation)
        val testRoots = distageTest.test.get.diKeys.toSet ++ forcedRoots
        val plan = if (testRoots.nonEmpty) injector.plan(PlannerInput(appModule, env.activation, testRoots)) else OrderedPlan.empty
        PreparedTest(distageTest, appModule, plan, env.activationInfo, env.activation, planner)
    }
    val envKeys = testPlans.flatMap(_.testPlan.keys).toSet

    // we need to "strengthen" all _memoized_ weak set instances that occur in our tests to ensure that they
    // be created and persist in memoized set. we do not use strengthened bindings afterwards, so non-memoized
    // weak sets behave as usual
    val (strengthenedKeys, strengthenedAppModule) = appModule.drop(runtimeKeys).foldLeftWith(List.empty[DIKey]) {
      case (acc, b @ SetElementBinding(key, r: ImplDef.ReferenceImpl, _, _)) if r.weak && (envKeys(key) || envKeys(r.key)) =>
        (key :: acc) -> b.copy(implementation = r.copy(weak = false))
      case (acc, b) =>
        acc -> b
    }

    val orderedPlans = if (env.memoizationRoots.keys.nonEmpty) {
      // we need to create plans for each level of memoization
      // every duplicated key will be removed
      // every empty memoization level (after keys filtering) will be removed
      env
        .memoizationRoots.keys.toList.sortBy(_._1).foldLeft((List.empty[TriSplittedPlan], Set.empty[DIKey])) {
          case ((acc, previousKeys), (_, keys)) =>
            val filteredKeys = keys.getActiveKeys(env.activation) -- previousKeys
            if (filteredKeys.nonEmpty) {
              val plan = prepareSharedPlan(envKeys, runtimeKeys, filteredKeys, env.activation, injector, strengthenedAppModule)
              (acc ++ List(plan), previousKeys ++ filteredKeys)
            } else {
              acc -> previousKeys
            }
        }._1
    } else {
      List(prepareSharedPlan(envKeys, runtimeKeys, Set.empty, env.activation, injector, strengthenedAppModule))
    }

    val envMergeCriteria = EnvMergeCriteria(bsPlanMinusUnstable, bsModuleMinusUnstable, runtimePlan, envExec)

    val memoEnvHashCode = envMergeCriteria.hashCode()
    val integrationLogger = lateLogger("memoEnv" -> memoEnvHashCode)
    val highestDebugOutputInTests = tests.exists(_.environment.debugOutput)
    if (strengthenedKeys.nonEmpty) {
      integrationLogger.log(testkitDebugMessagesLogLevel(env.debugOutput))(
        s"Strengthened weak components: $strengthenedKeys"
      )
    }

    PackedEnv(envMergeCriteria, testPlans, orderedPlans, injector, integrationLogger, highestDebugOutputInTests, strengthenedKeys.toSet)
  }

  def proceedEnvs(parallel: ParallelLevel)(envs: Iterable[(MemoizationEnv, MemoizationTree[F])]): Unit = {
    configuredForeach(parallel)(envs) {
      case (MemoizationEnv(envExec, integrationLogger, runtimePlan, memoizationInjector, _, allStrengthenedKeys), testsTree) =>
        val allEnvTests = testsTree.allTests.map(_.test)
        integrationLogger.info(s"Processing ${allEnvTests.size -> "tests"} using ${TagK[F].tag -> "monad"}")
        withRecoverFromFailedExecution_(allEnvTests) {
          val envIntegrationChecker = new IntegrationChecker.Impl[F](integrationLogger)
          val planChecker = new PlanCircularDependencyCheck(envExec.planningOptions, integrationLogger)

          // producing and verifying runtime plan
          assert(runtimeGcRoots.diff(runtimePlan.keys).isEmpty)
          planChecker.verify(runtimePlan)
          memoizationInjector.produceCustomF[Identity](runtimePlan).use {
            runtimeLocator =>
              val runner = runtimeLocator.get[DIEffectRunner[F]]
              implicit val F: DIEffect[F] = runtimeLocator.get[DIEffect[F]]
              implicit val P: DIEffectAsync[F] = runtimeLocator.get[DIEffectAsync[F]]

              runner.run {
                testsTree.stateTraverse(runtimeLocator) {
                  (locator, plan, allTests) => stateAction =>
                    withTestsRecoverCase(allTests.map(_.test)) {
                      withIntegrationSharedPlan(locator, planChecker, envIntegrationChecker, plan, allEnvTests)(stateAction)
                    }
                } {
                  (locator, tests) => proceedSuites(planChecker, locator, integrationLogger, allStrengthenedKeys)(tests)
                }
              }
          }
        }
    }
  }

  protected def withIntegrationSharedPlan(
    parentLocator: Locator,
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
    Injector.inherit(parentLocator).produceCustomF[F](plan.shared).use {
      sharedLocator =>
        // integration plan
        planCheck.verify(plan.side)
        Injector.inherit(sharedLocator).produceCustomF[F](plan.side).use {
          integrationSharedLocator =>
            withIntegrationCheck(checker, integrationSharedLocator)(tests, plan) {
              // main plan
              planCheck.verify(plan.primary)
              Injector.inherit(integrationSharedLocator).produceCustomF[F](plan.primary).use {
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
    checker.collectFailures(plans.sideRoots1, plans.sideRoots2, integrationLocator).flatMap {
      case Some(failures) =>
        F.maybeSuspend {
          ignoreIntegrationCheckFailedTests(tests, failures)
        }
      case None =>
        onSuccess
    }
  }

  protected def ignoreIntegrationCheckFailedTests(tests: Iterable[DistageTest[F]], failures: NonEmptyList[ResourceCheck.Failure]): Unit = {
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
      preparedTest =>
        val testId = preparedTest.test.meta.id
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
    val PreparedTest(test, appModule, testPlan, activationInfo, activation, planner) = preparedTest

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

    // FIXME: activation in boostrap locator doesn't change much now
    val testInjector = Injector.inherit(locatorWithOverridenActivation)

    val allSharedKeys = mainSharedLocator.allInstances.map(_.key).toSet

    val (testIntegrationCheckKeysIdentity, testIntegrationCheckKeysEffect) = {
      val (res1, res2) = testPlan.collectChildrenKeysSplit[IntegrationCheck[Identity], IntegrationCheck[F]]
      (res1 -- allSharedKeys, res2 -- allSharedKeys)
    }

    val newAppModule = appModule.drop(allSharedKeys)
    val moduleKeys = newAppModule.keys
    // there may be strengthened keys which did not get into shared context, so we need to manually declare them as roots
    val newRoots = testPlan.keys -- allSharedKeys ++ allStrengthenedKeys.intersect(moduleKeys)
    val newTestPlan = testInjector.trisectByRoots(activation, newAppModule, newRoots, testIntegrationCheckKeysIdentity, testIntegrationCheckKeysEffect)

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
    testInjector.produceCustomF[F](newTestPlan.shared).use {
      sharedLocator =>
        Injector.inherit(sharedLocator).produceCustomF[F](newTestPlan.side).use {
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
      Injector.inherit(parent).produceCustomF[F](testPlan).use {
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

  private[this] def logEnvironmentsInfo(envs: Map[MemoizationEnv, MemoizationTree[F]]): Unit = {
    val testRunnerLogger = {
      val minimumLogLevel = envs.map(_._1.envExec.logLevel).toSeq.sorted.headOption.getOrElse(Log.Level.Info)
      IzLogger(minimumLogLevel)("phase" -> "testRunner")
    }
    val originalEnvSize = envs.iterator.flatMap(_._2.allTests.map(_.test.environment)).toSet.size
    val memoizationTreesNum = envs.size

    testRunnerLogger.info(
      s"Created ${memoizationTreesNum -> "memoization trees"} with ${envs.iterator.flatMap(_._2.allTests).size -> "tests"} using ${TagK[F].tag -> "monad"}"
    )
    if (originalEnvSize != memoizationTreesNum) {
      testRunnerLogger.info(s"Merged together ${(originalEnvSize - memoizationTreesNum) -> "raw environments"}")
    }

    envs.foreach {
      case (MemoizationEnv(_, testEnvLogger, runtimePlan, _, debugOutput, _), testTree) =>
        val suites = testTree.allTests.map(_.test.meta.id.suiteClassName).toList.distinct
        testEnvLogger.info(
          s"Memoization environment with ${suites.size -> "suites"} ${testTree.allTests.size -> "tests"} ${testTree.toString -> "suitesMemoizationTree"}"
        )
        testEnvLogger.log(testkitDebugMessagesLogLevel(debugOutput))(
          s"""Effect runtime plan: $runtimePlan"""
//             |
//             |Memoized pre-integration plan: ${memoizationPlan.shared}
//             |
//             |Memoized integration plan: ${memoizationPlan.side}
//             |
//             |Memoized primary plan: ${memoizationPlan.primary}""".stripMargin
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
    bsPlanMinusActivations: Vector[ExecutableOp],
    bsModuleMinusActivations: BootstrapModule,
    runtimePlan: OrderedPlan,
    envExec: EnvExecutionParams,
  )
  final case class PackedEnv[F[_]](
    envMergeCriteria: EnvMergeCriteria,
    preparedTests: Seq[PreparedTest[F]],
    memoizationPlanTree: List[TriSplittedPlan],
    anyMemoizationInjector: Injector[Identity],
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
    final case class Ignored(checks: NonEmptyList[ResourceCheck.Failure]) extends Done

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
    def unapply(arg: ProvisioningException): Option[NonEmptyList[ResourceCheck.Failure]] = {
      NonEmptyList.from(arg.getSuppressed.iterator.collect { case i: IntegrationCheckException => i.failures.toList }.flatten.toList)
    }
  }

  /**
    * Structure for creation, storage and traversing over memoization levels.
    * To support the memoization level we should create a memoization tree first, where every node will contain a unique part of the memoization plan.
    * For better performance we are going to use mutable structures. Mutate this tree only in case when you KNOW what you doing.
    * Every change in tree structure may lead to test failed across all childs of the corrupted node.
    */
  final class MemoizationTree[F[_]] {
    private[this] val childs = TrieMap.empty[TriSplittedPlan, MemoizationTree[F]]
    private[this] val nodeTests = ArrayBuffer.empty[PreparedTest[F]]

    @tailrec def addTests(plans: List[TriSplittedPlan], preparedTests: Iterable[PreparedTest[F]]): Unit = synchronized {
      // here we are filtering all empty plans to merge levels together
      plans.filter(_.nonEmpty) match {
        case plan :: tail =>
          val childTree = childs.getOrElseUpdate(plan, new MemoizationTree[F])
          childTree.addTests(tail, preparedTests)
        case Nil =>
          nodeTests.appendAll(preparedTests)
          ()
      }
    }

    @inline private def stateTraverse_[State](
      initialState: State,
      thisPlan: TriSplittedPlan,
    )(stateAcquire: (State, TriSplittedPlan, => Iterable[PreparedTest[F]]) => (State => F[Unit]) => F[Unit]
    )(stateAction: (State, Iterable[PreparedTest[F]]) => F[Unit]
    )(implicit F: DIEffect[F]
    ): F[Unit] = {
      stateAcquire(initialState, thisPlan, allTests)(stateTraverse(_)(stateAcquire)(stateAction))
    }

    /** Root node traverse. User should never call children node directly. */
    @inline def stateTraverse[State](
      initialState: State
    )(stateAcquire: (State, TriSplittedPlan, => Iterable[PreparedTest[F]]) => (State => F[Unit]) => F[Unit]
    )(stateAction: (State, Iterable[PreparedTest[F]]) => F[Unit]
    )(implicit F: DIEffect[F]
    ): F[Unit] = {
      for {
        _ <- stateAction(initialState, nodeTests.toSeq)
        _ <- F.traverse_(childs) { case (plan, other) => other.stateTraverse_(initialState, plan)(stateAcquire)(stateAction) }
      } yield ()
    }

    @inline def allTests: Iterable[PreparedTest[F]] = {
      nodeTests.toList ++ childs.flatMap(_._2.allTests)
    }

    @inline private def toString_(level: Int = 0): String = {
      val currentLevel = List.fill(level)("|  ").mkString
      val currentLevelPad = s"\n${List.fill(level)("|__").mkString}Level=$level"
      val testIds = nodeTests.toList.map(_.test.meta.id.suiteName).distinct.sorted.map(t => s"$currentLevel|__$t")
      val str = if (testIds.nonEmpty) s"$currentLevelPad\n${testIds.mkString("\n")}" else currentLevelPad
      childs.toList.foldLeft(str) { case (acc, (_, next)) => s"$acc${next.toString_(level + 1)}" }
    }

    @inline override def toString: String = toString_()
  }
  object MemoizationTree {
    def apply[F[_]](iterator: Iterable[PackedEnv[F]]): MemoizationTree[F] = {
      val tree = new MemoizationTree[F]
      iterator.foreach(env => tree.addTests(env.memoizationPlanTree, env.preparedTests))
      tree
    }
  }

  private val enableDebugOutput: Boolean = DebugProperties.`izumi.distage.testkit.debug`.boolValue(false)

  private final val memoizedConfig = new ConcurrentHashMap[(String, BootstrapFactory, Option[AppConfig]), AppConfig]

}
