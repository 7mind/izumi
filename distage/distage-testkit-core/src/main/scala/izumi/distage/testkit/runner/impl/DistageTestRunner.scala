package izumi.distage.testkit.runner.impl

import distage.*
import izumi.distage.modules.DefaultModule
import izumi.distage.testkit.model.*
import izumi.distage.testkit.model.TestConfig.Parallelism
import izumi.distage.testkit.runner.api.TestReporter
import izumi.distage.testkit.runner.impl.TestPlanner.*
import izumi.distage.testkit.runner.impl.services.{TestStatusConverter, TestkitLogging, TimedAction, Timing}
import izumi.functional.quasi.QuasiIO.syntax.*
import izumi.functional.quasi.{QuasiAsync, QuasiIO, QuasiIORunner}
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.uuid.UUIDGen
import izumi.logstage.api.IzLogger
import logstage.Log

import scala.concurrent.duration.FiniteDuration

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
  def run(tests: Seq[DistageTest[F]]): List[EnvResult] = {
    runF[Identity](tests, timedActionId)
  }

  def runF[G[_]](tests: Seq[DistageTest[F]], timed: TimedAction[G])(implicit G: QuasiIO[G]): G[List[EnvResult]] = {
    // We assume that under normal cirsumstances the code below should never throw.
    // All the exceptions should be converted to values by this time.
    // If it throws, there is a bug which needs to be fixed.
    for {
      id <- G.maybeSuspend(ScopeId(UUIDGen.getTimeUUID()))
      _ <- G.maybeSuspend(reporter.beginScope(id))
      envs <- timed.timed(G.maybeSuspend(planner.groupTests(tests)))
      _ <- G.maybeSuspend(reportFailedPlanning(id, envs.out.bad, envs.timing))
      // TODO: there shouldn't be a case with more than one tree per env, maybe we should assert/fail instead
      toRun <- G.pure(envs.out.good.flatMap(_.envs.toSeq).groupBy(_._1).flatMap(_._2))
      _ <- G.maybeSuspend(logEnvironmentsInfo(toRun, envs.timing.duration))
      result <- G.maybeSuspend(runTests(id, toRun))
      _ <- G.maybeSuspend(reporter.endScope(id))
    } yield {
      result
    }
  }

  private def runTests(id: ScopeId, toRun: Map[PreparedTestEnv, TestTree[F]]): List[EnvResult] = {
    configuredParTraverse[Identity, (PreparedTestEnv, TestTree[F]), List[EnvResult]](toRun)(_._1.envExec.parallelEnvs) {
      case (e, t) => List(proceedEnv(id, e, t))
    }.flatten
  }

  private def reportFailedPlanning(id: ScopeId, bad: Seq[(Seq[DistageTest[F]], PlanningFailure)], timing: Timing): Unit = {
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
            reporter.testSetupStatus(id, test.meta, TestStatus.FailedInitialPlanning(failure, asThrowable, timing))
        }
    }
  }

  protected def proceedEnv(id: ScopeId, env: PreparedTestEnv, testsTree: TestTree[F]): EnvResult = {
    val PreparedTestEnv(_, runtimePlan, runtimeInjector, _) = env

    val allEnvTests = testsTree.allTests.map(_.test)

    timedActionId.timed(runtimeInjector.produceDetailedCustomF[Identity](runtimePlan)).use {
      maybeRtLocator =>
        maybeRtLocator.mapMerge(
          {
            (runtimeInstantiationFailure, runtimeInstantiationTiming) =>
              val result = EnvResult.RuntimePlanningFailure(runtimeInstantiationTiming, allEnvTests.map(_.meta), runtimeInstantiationFailure)

              val failure = statusConverter.failRuntimePlanning(result)
              // fail all tests (if an exception reaches here, it must have happened before the runtime was successfully produced)
              allEnvTests.foreach {
                test => reporter.testSetupStatus(id, test.meta, failure)
              }

              result
          },
          {
            (runtimeLocator, runtimeInstantiationTiming) =>
              val runner = runtimeLocator.get[QuasiIORunner[F]]
              implicit val F: QuasiIO[F] = runtimeLocator.get[QuasiIO[F]]
              implicit val P: QuasiAsync[F] = runtimeLocator.get[QuasiAsync[F]]

              runtimeLocator
                .get[IzLogger]("distage-testkit")
                .info(s"Processing ${allEnvTests.size -> "tests"} using ${TagK[F].tag -> "monad"}")

              EnvResult.EnvSuccess(runtimeInstantiationTiming, runner.run(traverse(id, 0, testsTree, runtimeLocator)))
          },
        )

    }
  }

  private def traverse(id: ScopeId, depth: Int, tree: TestTree[F], parent: Locator)(implicit F: QuasiIO[F], P: QuasiAsync[F]): F[List[GroupResult]] = {
    timedAction.timed(Injector.inherit(parent).produceDetailedCustomF[F](tree.levelPlan)).use {
      maybeLocator =>
        maybeLocator.mapMerge(
          {
            case (levelInstantiationFailure, levelInstantiationTiming) =>
              F.maybeSuspend {
                val all = tree.allTests.map(_.test)
                val result = GroupResult.EnvLevelFailure(all.map(_.meta), levelInstantiationFailure, levelInstantiationTiming)
                val failure = statusConverter.failLevelInstantiation(result)
                all.foreach {
                  test => reporter.testStatus(id, depth, test.meta, failure)
                }
                List(result: GroupResult)
              }
          },
          {
            case (levelLocator, levelInstantiationTiming) =>
              for {
                results <- proceedMemoizationLevel(id, depth, levelLocator, tree.groups)
                subResults <- F.traverse(tree.nested) {
                  nextTree =>
                    traverse(id, depth + 1, nextTree, levelLocator)
                }
              } yield {
                List(GroupResult.GroupSuccess(results, levelInstantiationTiming)) ++ subResults.flatten
              }
          },
        )
    }
  }

  private def proceedMemoizationLevel(
    id: ScopeId,
    depth: Int,
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
          acquire = F.maybeSuspend(reporter.beginLevel(id, depth, suiteData.meta))
        )(release = _ => F.maybeSuspend(reporter.endLevel(id, depth, suiteData.meta))) {
          _ =>
            configuredParTraverse(preparedTests)(_.test.environment.parallelTests) {
              test => individualTestRunner.proceedTest(id, depth, deepestSharedLocator, suiteData.strengthenedKeys, test)
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

  private[this] def logEnvironmentsInfo(envs: Map[PreparedTestEnv, TestTree[F]], duration: FiniteDuration): Unit = {
    val testRunnerLogger = {
      val minimumLogLevel = envs.map(_._1.envExec.logLevel).toSeq.sorted.headOption.getOrElse(Log.Level.Info)
      IzLogger(minimumLogLevel)("phase" -> "testRunner")
    }
    testRunnerLogger.info(s"Test planning took ${duration.toMillis} ...")
    val originalEnvSize = envs.iterator.flatMap(_._2.allTests.map(_.test.environment)).toSet.size
    val memoizationTreesNum = envs.size

    testRunnerLogger.info(
      s"Created ${memoizationTreesNum -> "memoization trees"} with ${envs.iterator.flatMap(_._2.allTests).size -> "tests"} using ${TagK[F].tag -> "monad"}"
    )
    if (originalEnvSize != memoizationTreesNum) {
      testRunnerLogger.info(s"Merged together ${(originalEnvSize - memoizationTreesNum) -> "raw environments"}")
    }

    envs.foreach {
      case (PreparedTestEnv(_, runtimePlan, _, debugOutput), testTree) =>
        val suites = testTree.allTests.map(_.test.suiteMeta.suiteClassName).toList.distinct
        testRunnerLogger.info(
          s"Memoization environment with ${suites.size -> "suites"} ${testTree.allTests.size -> "tests"} ${testTree.repr -> "suitesMemoizationTree"}"
        )
        testRunnerLogger.log(logging.testkitDebugMessagesLogLevel(debugOutput))(
          s"""Effect runtime plan: $runtimePlan"""
        )
    }
  }

}
