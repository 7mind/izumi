package izumi.distage.testkit.runner

import distage.*
import izumi.distage.framework.services.PlanCircularDependencyCheck
import izumi.distage.modules.DefaultModule
import izumi.distage.testkit.model.*
import izumi.distage.testkit.model.TestConfig.Parallelism
import izumi.distage.testkit.runner.MemoizationTree.TestGroup
import izumi.distage.testkit.runner.TestPlanner.*
import izumi.distage.testkit.runner.api.TestReporter
import izumi.distage.testkit.runner.services.{ReporterBracket, TestkitLogging}
import izumi.functional.quasi.{QuasiAsync, QuasiIO, QuasiIORunner}
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.time.IzTime
import izumi.logstage.api.{IzLogger, Log}

import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

object DistageTestRunner {
  case class SuiteData(id: SuiteId, meta: SuiteMeta, suiteParallelism: Parallelism, strengthenedKeys: Set[DIKey])
}

class DistageTestRunner[F[_]: TagK: DefaultModule](
  reporter: TestReporter,
  logging: TestkitLogging,
  planner: TestPlanner[F],
  individualTestRunner: IndividualTestRunner[F],
  reporterBracket: ReporterBracket[F],
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

    val beforeAll = System.nanoTime()
    try {
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
            }(recover = {
              tree =>
                {
                  action: F[Unit] =>
                    val subtreeTests = tree.getAllTests.map(_.test)
                    val beforeLevel = System.nanoTime()
                    F.definitelyRecoverCause(action) {
                      case (t, trace) =>
                        F.maybeSuspend {
                          val failure = reporterBracket.fail(beforeLevel)(t, trace)
                          subtreeTests.foreach {
                            test => reporter.testStatus(test.meta, failure)
                          }
                        }
                    }
                }
            })
          }
      }
    } catch {
      case t: Throwable =>
        // fail all tests (if an exception reaches here, it must have happened before the runtime was successfully produced)
        allEnvTests.foreach {
          test => reporter.testStatus(test.meta, reporterBracket.fail(beforeAll)(t, () => t))
        }
    }
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
            val testId = preparedTest.test.meta.test.id
            val parallelLevel = preparedTest.test.environment.parallelSuites
            DistageTestRunner.SuiteData(testId.suite, preparedTest.test.suiteMeta, parallelLevel, strengthenedKeys)
        }
    }
    // now we are ready to run each individual test
    // note: scheduling here is custom also and tests may automatically run in parallel for any non-trivial monad
    // we assume that individual tests within a suite can't have different values of `parallelSuites`
    // (because of structure & that difference even if happens wouldn't be actionable at the level of suites anyway)
    configuredParTraverse(testsBySuite)(_._1.suiteParallelism) {
      case (suiteData, preparedTests) =>
        F.bracket(
          acquire = F.maybeSuspend(reporter.beginSuite(suiteData.meta))
        )(release = _ => F.maybeSuspend(reporter.endSuite(suiteData.meta))) {
          _ =>
            configuredParTraverse(preparedTests)(_.test.environment.parallelTests) {
              test => individualTestRunner.proceedTest(planChecker, deepestSharedLocator, testRunnerLogger, suiteData.strengthenedKeys)(test)
            }
        }
    }
  }

  protected def configuredParTraverse[F1[_], A](
    l: Iterable[A]
  )(getParallelismGroup: A => Parallelism
  )(f: A => F1[Unit]
  )(implicit
    F: QuasiIO[F1],
    P: QuasiAsync[F1],
  ): F1[Unit] = {
    val sorted = l.groupBy(getParallelismGroup).toList.sortBy {
      case (Parallelism.Unlimited, _) => 1
      case (Parallelism.Fixed(_), _) => 2
      case (Parallelism.Sequential, _) => 3
    }
    F.traverse_(sorted) {
      case (Parallelism.Fixed(n), l) if l.size > 1 => P.parTraverseN_(n)(l)(f)
      case (Parallelism.Unlimited, l) if l.size > 1 => P.parTraverse_(l)(f)
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
        val suites = testTree.getAllTests.map(_.test.suiteMeta.suiteClassName).toList.distinct
        testRunnerLogger.info(
          s"Memoization environment with ${suites.size -> "suites"} ${testTree.getAllTests.size -> "tests"} ${testTree -> "suitesMemoizationTree"}"
        )
        testRunnerLogger.log(logging.testkitDebugMessagesLogLevel(debugOutput))(
          s"""Effect runtime plan: $runtimePlan"""
        )
    }
  }

}
