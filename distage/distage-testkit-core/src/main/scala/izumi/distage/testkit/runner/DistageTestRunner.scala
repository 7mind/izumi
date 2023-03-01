package izumi.distage.testkit.runner

import distage.*
import izumi.distage.modules.DefaultModule
import izumi.distage.testkit.model.*
import izumi.distage.testkit.model.TestConfig.Parallelism
import izumi.distage.testkit.runner.MemoizationTree.TestGroup
import izumi.distage.testkit.runner.TestPlanner.*
import izumi.distage.testkit.runner.api.TestReporter
import izumi.distage.testkit.runner.services.{TestStatusConverter, TestkitLogging, TimedAction}
import izumi.functional.quasi.QuasiIO.syntax.*
import izumi.functional.quasi.{QuasiAsync, QuasiIO, QuasiIORunner}
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.Quirks.Discarder
import izumi.logstage.api.{IzLogger, Log}

import scala.concurrent.duration.{Duration, FiniteDuration}

object DistageTestRunner {
  case class SuiteData(id: SuiteId, meta: SuiteMeta, suiteParallelism: Parallelism, strengthenedKeys: Set[DIKey])
}

class DistageTestRunner[F[_]: TagK: DefaultModule](
  reporter: TestReporter,
  logging: TestkitLogging,
  planner: TestPlanner[F],
  individualTestRunner: IndividualTestRunner[F],
  statusConverter: TestStatusConverter[F],
  timedAction: TimedAction[F],
  timedActionId: TimedAction[Identity],
) {
  timedAction.discard()

  def run(tests: Seq[DistageTest[F]]): List[EnvResult] = {
    // We assume that under normal cirsumstances the code below should never throw.
    // All the exceptions should be converted to values by this time.
    // If it throws, there is a bug which needs to be fixed.
    for {
      envs <- timedActionId.timed(planner.groupTests(tests))
      _ <- QuasiIO[Identity].maybeSuspend(reportFailedPlanning(envs.out.bad))
      // TODO: there shouldn't be a case with more than one tree per env, maybe we should assert/fail instead
      toRun = envs.out.good.flatMap(_.envs.toSeq).groupBy(_._1).flatMap(_._2)
      _ <- QuasiIO[Identity].maybeSuspend(logEnvironmentsInfo(toRun, envs.timing.duration))
      result = runTests(toRun)
      _ <- QuasiIO[Identity].maybeSuspend(reporter.endScope())
    } yield {
      result
    }
  }

  private def runTests(toRun: Map[PreparedTestEnv, MemoizationTree[F]]): List[EnvResult] = {
    configuredParTraverse[Identity, (PreparedTestEnv, MemoizationTree[F]), List[EnvResult]](toRun)(_._1.envExec.parallelEnvs) {
      case (e, t) => List(proceedEnv(e, t))
    }.flatten
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
            reporter.testStatus(test.meta, TestStatus.Failed(asThrowable, Duration.Zero))
        }
    }
  }

  protected def proceedEnv(env: PreparedTestEnv, testsTree: MemoizationTree[F]): EnvResult = {
    val PreparedTestEnv(_, runtimePlan, runtimeInjector, _) = env

    val allEnvTests = testsTree.getAllTests.map(_.test)

    for {
      maybeRtPlan <- timedActionId.timed(runtimeInjector.produceDetailedCustomF[Identity](runtimePlan)).use {
        maybeRtLocator =>
          maybeRtLocator.mapMerge(
            {
              (fail, t) =>
                val failure = statusConverter.fail(t.duration, fail.toThrowable)
                // fail all tests (if an exception reaches here, it must have happened before the runtime was successfully produced)
                allEnvTests.foreach {
                  test => reporter.testStatus(test.meta, failure)
                }
                EnvResult.RuntimePlanningFailure(t, allEnvTests.map(_.meta), fail)
            },
            {
              (runtimeLocator, t) =>
                val runner = runtimeLocator.get[QuasiIORunner[F]]
                implicit val F: QuasiIO[F] = runtimeLocator.get[QuasiIO[F]]
                implicit val P: QuasiAsync[F] = runtimeLocator.get[QuasiAsync[F]]

                runtimeLocator
                  .get[IzLogger]("distage-testkit")
                  .info(s"Processing ${allEnvTests.size -> "tests"} using ${TagK[F].tag -> "monad"}")

                EnvResult.EnvSuccess(t, runner.run(traverse(testsTree, runtimeLocator)))
            },
          )

      }
    } yield {
      maybeRtPlan
    }
  }

  private def traverse(tree: MemoizationTree[F], parent: Locator)(implicit F: QuasiIO[F], P: QuasiAsync[F]): F[List[GroupResult]] = {
    timedAction.timed(Injector.inherit(parent).produceDetailedCustomF[F](tree.plan)).use {
      maybeLocator =>
        maybeLocator.mapMerge(
          {
            case (f, t) =>
              F.maybeSuspend {
                val failure = statusConverter.fail(t.duration, f.toThrowable)
                val all = tree.getAllTests.map(_.test)
                all.foreach {
                  test => reporter.testStatus(test.meta, failure)
                }
                List(GroupResult.EnvLevelFailure(all.map(_.meta), f, t): GroupResult)
              }
          },
          {
            case (locator, t) =>
              for {
                results <- proceedMemoizationLevel(locator, tree.getGroups)
                subResults <- F.traverse(tree.next) {
                  nextTree =>
                    traverse(nextTree, locator)
                }
              } yield {
                List(GroupResult.GroupSuccess(results, t)) ++ subResults.flatten
              }
          },
        )
    }
  }

  private def proceedMemoizationLevel(
    deepestSharedLocator: Locator,
    levelGroups: Iterable[TestGroup[F]],
  )(implicit
    F: QuasiIO[F],
    P: QuasiAsync[F],
  ): F[List[IndividualTestResult]] = {
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
              test => individualTestRunner.proceedTest(deepestSharedLocator, suiteData.strengthenedKeys, test)
            }
        }
    }.map(_.flatten)
  }

  protected def configuredParTraverse[F1[_], A, B](
    l: Iterable[A]
  )(getParallelismGroup: A => Parallelism
  )(f: A => F1[B]
  )(implicit
    F: QuasiIO[F1],
    P: QuasiAsync[F1],
  ): F1[List[B]] = {
    val sorted = l.groupBy(getParallelismGroup).toList.sortBy {
      case (Parallelism.Unlimited, _) => 1
      case (Parallelism.Fixed(_), _) => 2
      case (Parallelism.Sequential, _) => 3
    }
    F.traverse(sorted) {
      case (Parallelism.Fixed(n), l) if l.size > 1 => P.parTraverseN(n)(l)(f)
      case (Parallelism.Unlimited, l) if l.size > 1 => P.parTraverse(l)(f)
      case (_, l) => F.traverse(l)(f)
    }.map(_.flatten)
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
      case (PreparedTestEnv(_, runtimePlan, _, debugOutput), testTree) =>
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
