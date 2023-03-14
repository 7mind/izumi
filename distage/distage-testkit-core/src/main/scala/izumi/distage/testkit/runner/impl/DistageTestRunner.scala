package izumi.distage.testkit.runner.impl

import distage.*
import izumi.distage.testkit.model.*
import izumi.distage.testkit.model.TestConfig.Parallelism
import izumi.distage.testkit.runner.api.TestReporter
import izumi.distage.testkit.runner.impl.TestPlanner.*
import izumi.distage.testkit.runner.impl.services.*
import izumi.functional.quasi.QuasiIO.syntax.*
import izumi.functional.quasi.{QuasiIO, QuasiIORunner}
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.uuid.UUIDGen
import izumi.logstage.api.IzLogger
import logstage.Log

import scala.concurrent.duration.FiniteDuration

object DistageTestRunner {
  case class SuiteData(id: SuiteId, meta: SuiteMeta, suiteParallelism: Parallelism)
}

class DistageTestRunner[F[_]: TagK](
  reporter: TestReporter,
  logging: TestkitLogging,
  planner: TestPlanner[F],
  statusConverter: TestStatusConverter,
  timed: TimedActionF[Identity],
  parTraverse: ExtParTraverse[Identity],
) {

  def run(tests: Seq[DistageTest[F]]): List[EnvResult] = {
    runF[Identity](tests, timed)
  }

  def runF[G[_]](tests: Seq[DistageTest[F]], timed: TimedActionF[G])(implicit G: QuasiIO[G]): G[List[EnvResult]] = {
    // We assume that under normal cirsumstances the code below should never throw.
    // All the exceptions should be converted to values by this time.
    // If it throws, there is a bug which needs to be fixed.
    for {
      id <- G.maybeSuspend(ScopeId(UUIDGen.getTimeUUID()))
      _ <- G.maybeSuspend(reporter.beginScope(id))
      envs <- timed(G.maybeSuspend(planner.groupTests(tests)))
      _ <- G.maybeSuspend(reportFailedPlanning(id, envs.out.bad, envs.timing))
      _ <- G.maybeSuspend(reportFailedInvividualPlans(id, envs))
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
    parTraverse(toRun)(_._1.envExec.parallelEnvs) {
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

  private def reportFailedInvividualPlans(id: ScopeId, envs: Timed[PlannedTests[F]]): Unit = {
    val failures = envs.out.good.flatMap(_.envs.flatMap(_._2.allFailures))

    failures.foreach {
      ft =>
        reporter.testSetupStatus(id, ft.test.meta, TestStatus.FailedPlanning(ft.timedPlan.timing, ft.timedPlan.out.aggregateErrors))
    }
  }

  protected def proceedEnv(id: ScopeId, env: PreparedTestEnv, testsTree: TestTree[F]): EnvResult = {
    val PreparedTestEnv(_, runtimePlan, runtimeInjector, _) = env

    val allEnvTests = testsTree.allTests.map(_.test)

    timed(runtimeInjector.produceDetailedCustomF[Identity](runtimePlan)).use {
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
              val testTreeRunner = runtimeLocator.get[TestTreeRunner[F]]

              runtimeLocator
                .get[IzLogger]("distage-testkit")
                .info(s"Processing ${allEnvTests.size -> "tests"} using ${TagK[F].tag -> "monad"}")

              EnvResult.EnvSuccess(runtimeInstantiationTiming, runner.run(testTreeRunner.traverse(id, 0, testsTree, runtimeLocator)))
          },
        )

    }
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
