package izumi.distage.testkit.runner

import distage.*
import izumi.distage.framework.services.PlanCircularDependencyCheck
import izumi.distage.model.exceptions.runtime.ProvisioningIntegrationException
import izumi.distage.model.plan.Plan
import izumi.distage.modules.DefaultModule
import izumi.distage.testkit.model.*
import izumi.distage.testkit.model.TestConfig.ParallelLevel
import izumi.distage.testkit.runner.MemoizationTree.TestGroup
import izumi.distage.testkit.runner.TestPlanner.*
import izumi.distage.testkit.runner.api.TestReporter
import izumi.distage.testkit.runner.services.{ReporterBracket, TestkitLogging}
import izumi.functional.quasi.QuasiIO.syntax.*
import izumi.functional.quasi.{QuasiAsync, QuasiIO, QuasiIORunner}
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.time.IzTime
import izumi.logstage.api.{IzLogger, Log}

import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{Duration, FiniteDuration}

class DistageTestRunner[F[_]: TagK: DefaultModule](
  reporter: TestReporter,
  isTestSkipException: Throwable => Boolean,
  reporterBracket: ReporterBracket[F],
  logging: TestkitLogging,
  planner: TestPlanner[F],
) {
  def run(tests: Seq[DistageTest[F]]): Unit = {
    try {
      val start = IzTime.utcNowOffset
      val envs = planner.groupTests(tests)
      val end = IzTime.utcNowOffset
      val planningTime = FiniteDuration(ChronoUnit.NANOS.between(start, end), TimeUnit.NANOSECONDS)

      reportFailedPlanning(envs.bad)
      // TODO: there shouldn't be a case with more than one tree per env, maybe we should assert/fail instead
      val toRun = envs.good.flatMap(_.envs.toSeq).groupBy(_._1).flatMap(_._2)
      logEnvironmentsInfo(toRun, planningTime)
      runTests(toRun, planner.runtimeGcRoots)

    } catch {
      case t: Throwable =>
        reporter.onFailure(t)
    } finally {
      reporter.endScope()
    }
  }

  private def runTests(toRun: Map[PreparedTestEnv, MemoizationTree[F]], runtimeRoots: Set[DIKey]): Unit = {
    configuredParTraverse[Identity, (PreparedTestEnv, MemoizationTree[F])](toRun)(_._1.envExec.parallelEnvs) {
      case (e, t) => proceedEnv(e, t, runtimeRoots)
    }
  }

  private def reportFailedPlanning(bad: Seq[(Seq[DistageTest[F]], PlanningFailure)]): Unit = {
    bad.foreach {
      case (badTests, failure) =>
        badTests.foreach {
          test =>
            val asThrowable = failure match {
              case PlanningFailure.Exception(throwable) =>
                throwable
              case PlanningFailure.DIErrors(errors) =>
                errors.aggregateErrors
            }
            reporter.testStatus(test.meta, TestStatus.Failed(asThrowable, FiniteDuration.apply(0, TimeUnit.MILLISECONDS)))
        }
    }
  }

  protected def proceedEnv(env: PreparedTestEnv, testsTree: MemoizationTree[F], runtimeGcRoots: Set[DIKey]): Unit = {
    val PreparedTestEnv(envExec, integrationLogger, runtimePlan, memoizationInjector, _) = env
    val allEnvTests = testsTree.getAllTests.map(_.test)
    integrationLogger.info(s"Processing ${allEnvTests.size -> "tests"} using ${TagK[F].tag -> "monad"}")
    reporterBracket.withRecoverFromFailedExecution(allEnvTests) {
      val planChecker = new PlanCircularDependencyCheck(envExec.planningOptions, integrationLogger)

      // producing and verifying runtime plan
      assert(runtimeGcRoots.diff(runtimePlan.keys).isEmpty)
      planChecker.showProxyWarnings(runtimePlan)
      memoizationInjector.produceCustomF[Identity](runtimePlan).use {
        runtimeLocator =>
          val runner = runtimeLocator.get[QuasiIORunner[F]]
          implicit val F: QuasiIO[F] = runtimeLocator.get[QuasiIO[F]]
          implicit val P: QuasiAsync[F] = runtimeLocator.get[QuasiAsync[F]]

          runner.run {
            testsTree.stateTraverseLifecycle(runtimeLocator) {
              case (locator, tree) =>
                planChecker.showProxyWarnings(tree.plan)
                Injector.inherit(locator).produceCustomF[F](tree.plan).evalTap {
                  mainSharedLocator =>
                    proceedMemoizationLevel(planChecker, mainSharedLocator, integrationLogger)(tree.getGroups)
                }
            }(recover = tree => withTestsRecoverCause(None, tree.getAllTests.map(_.test))(_))
          }
      }
    }(onError = ())
  }

  protected def proceedMemoizationLevel(
    planChecker: PlanCircularDependencyCheck,
    deepestSharedLocator: Locator,
    testRunnerLogger: IzLogger,
  )(levelGroups: Iterable[TestGroup[F]]
  )(implicit
    F: QuasiIO[F],
    P: QuasiAsync[F],
  ): F[Unit] = {
    val testsBySuite = levelGroups.flatMap {
      case TestGroup(preparedTests, strengthenedKeys) =>
        preparedTests.groupBy {
          preparedTest =>
            val testId = preparedTest.test.meta.id
            val parallelLevel = preparedTest.test.environment.parallelSuites
            SuiteData(testId.suiteName, testId.suiteId, testId.suiteClassName, parallelLevel) -> strengthenedKeys
        }
    }
    // now we are ready to run each individual test
    // note: scheduling here is custom also and tests may automatically run in parallel for any non-trivial monad
    // we assume that individual tests within a suite can't have different values of `parallelSuites`
    // (because of structure & that difference even if happens wouldn't be actionable at the level of suites anyway)
    configuredParTraverse(testsBySuite)(_._1._1.parallelLevel) {
      case ((suiteData, strengthenedKeys), preparedTests) =>
        F.bracket(
          acquire = F.maybeSuspend(reporter.beginSuite(suiteData))
        )(release = _ => F.maybeSuspend(reporter.endSuite(suiteData))) {
          _ =>
            configuredParTraverse(preparedTests)(_.test.environment.parallelTests) {
              test => proceedTest(planChecker, deepestSharedLocator, testRunnerLogger, strengthenedKeys)(test)
            }
        }
    }
  }

  protected def proceedTest(
    planChecker: PlanCircularDependencyCheck,
    mainSharedLocator: Locator,
    testRunnerLogger: IzLogger,
    groupStrengthenedKeys: Set[DIKey],
  )(preparedTest: PreparedTest[F]
  )(implicit F: QuasiIO[F]
  ): F[Unit] = {
    val PreparedTest(test, appModule, testPlan, activation) = preparedTest

    val testInjector = Injector.inherit(mainSharedLocator)

    val allSharedKeys = mainSharedLocator.allInstances.map(_.key).toSet
    val newAppModule = appModule.drop(allSharedKeys)
    val newRoots = testPlan.keys -- allSharedKeys ++ groupStrengthenedKeys.intersect(newAppModule.keys)
    val maybeNewTestPlan = if (newRoots.nonEmpty) {
      testInjector.plan(PlannerInput(newAppModule, activation, newRoots)).aggregateErrors
    } else {
      Right(Plan.empty)
    }

    maybeNewTestPlan match {
      case Left(value) =>
        F.maybeSuspend(reporter.testStatus(test.meta, TestStatus.Failed(value, Duration.Zero)))

      case Right(newTestPlan) =>
        val testLogger = testRunnerLogger("testId" -> test.meta.id)
        testLogger.log(logging.testkitDebugMessagesLogLevel(test.environment.debugOutput))(
          s"""Running test...
             |
             |Test plan: $newTestPlan""".stripMargin
        )

        planChecker.showProxyWarnings(newTestPlan)

        proceedIndividual(test, newTestPlan, testInjector)
    }
  }

  protected def proceedIndividual(test: DistageTest[F], testPlan: Plan, testInjector: Injector[F])(implicit F: QuasiIO[F]): F[Unit] = {
    withTestsRecoverCause(None, Seq(test)) {
      if ((logging.enableDebugOutput || test.environment.debugOutput) && testPlan.keys.nonEmpty) {
        reporter.testInfo(test.meta, s"Test plan info: $testPlan")
      }
      testInjector.produceCustomF[F](testPlan).use {
        testLocator =>
          F.suspendF {
            val before = System.nanoTime()
            reporter.testStatus(test.meta, TestStatus.Running)

            withTestsRecoverCause(Some(before), Seq(test)) {
              testLocator
                .run(test.test)
                .flatMap(_ => F.maybeSuspend(reporter.testStatus(test.meta, TestStatus.Succeed(testDuration(Some(before))))))
            }
          }
      }
    }
  }

  protected def withTestsRecoverCause(before: Option[Long], tests: Seq[DistageTest[F]])(testsAction: => F[Unit])(implicit F: QuasiIO[F]): F[Unit] = {
    F.definitelyRecoverCause(testsAction) {
      case (s, _) if isTestSkipException(s) =>
        F.maybeSuspend {
          tests.foreach {
            test => reporter.testStatus(test.meta, TestStatus.Cancelled(s.getMessage, testDuration(before)))
          }
        }
      case (ProvisioningIntegrationException(failures), _) =>
        F.maybeSuspend {
          tests.foreach {
            test => reporter.testStatus(test.meta, TestStatus.Ignored(failures))
          }
        }
      case (_, getTrace) =>
        F.maybeSuspend {
          tests.foreach {
            test => reporter.testStatus(test.meta, TestStatus.Failed(getTrace(), testDuration(before)))
          }
        }
    }
  }

  private[this] def testDuration(before: Option[Long]): FiniteDuration = {
    before.fold(Duration.Zero) {
      before =>
        val after = System.nanoTime()
        FiniteDuration(after - before, TimeUnit.NANOSECONDS)
    }
  }

  protected def configuredParTraverse[F1[_], A](
    l: Iterable[A]
  )(getParallelismGroup: A => ParallelLevel
  )(f: A => F1[Unit]
  )(implicit
    F: QuasiIO[F1],
    P: QuasiAsync[F1],
  ): F1[Unit] = {
    val sorted = l.groupBy(getParallelismGroup).toList.sortBy {
      case (ParallelLevel.Unlimited, _) => 1
      case (ParallelLevel.Fixed(_), _) => 2
      case (ParallelLevel.Sequential, _) => 3
    }
    F.traverse_(sorted) {
      case (ParallelLevel.Fixed(n), l) if l.size > 1 => P.parTraverseN_(n)(l)(f)
      case (ParallelLevel.Unlimited, l) if l.size > 1 => P.parTraverse_(l)(f)
      case (_, l) => F.traverse_(l)(f)
    }
  }

  private[this] def logEnvironmentsInfo(envs: Map[PreparedTestEnv, MemoizationTree[F]], duration: FiniteDuration): Unit = {
    val testRunnerLogger = {
      val minimumLogLevel = envs.map(_._1.envExec.logLevel).toSeq.sorted.headOption.getOrElse(Log.Level.Info)
      IzLogger(minimumLogLevel)("phase" -> "testRunner")
    }
    testRunnerLogger.info(s"Test planning took ${duration.toMillis} ...")
    val originalEnvSize = envs.iterator.flatMap(_._2.getAllTests.map(_.test.environment)).toSet.size
    val memoizationTreesNum = envs.size

    testRunnerLogger.info(
      s"Created ${memoizationTreesNum -> "memoization trees"} with ${envs.iterator.flatMap(_._2.getAllTests).size -> "tests"} using ${TagK[F].tag -> "monad"}"
    )
    if (originalEnvSize != memoizationTreesNum) {
      testRunnerLogger.info(s"Merged together ${(originalEnvSize - memoizationTreesNum) -> "raw environments"}")
    }

    envs.foreach {
      case (PreparedTestEnv(_, _, runtimePlan, _, debugOutput), testTree) =>
        val suites = testTree.getAllTests.map(_.test.meta.id.suiteClassName).toList.distinct
        testRunnerLogger.info(
          s"Memoization environment with ${suites.size -> "suites"} ${testTree.getAllTests.size -> "tests"} ${testTree -> "suitesMemoizationTree"}"
        )
        testRunnerLogger.log(logging.testkitDebugMessagesLogLevel(debugOutput))(
          s"""Effect runtime plan: $runtimePlan"""
        )
    }
  }

}
