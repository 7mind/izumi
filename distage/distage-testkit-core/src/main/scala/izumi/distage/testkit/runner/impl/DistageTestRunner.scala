package izumi.distage.testkit.runner.impl

import distage.*
import izumi.distage.testkit.model.*
import izumi.distage.testkit.model.TestConfig.Parallelism
import izumi.distage.testkit.runner.api.TestReporter
import izumi.distage.testkit.runner.impl.TestPlanner.*
import izumi.distage.testkit.runner.impl.services.*
import izumi.functional.quasi.QuasiIO.syntax.*
import izumi.functional.quasi.{QuasiIO, QuasiIORunner}
import izumi.fundamentals.platform.language.types.HigherKindedAny.AnyF
import izumi.fundamentals.platform.uuid.IzUUID
import izumi.logstage.api.IzLogger
import logstage.Log

import scala.concurrent.duration.FiniteDuration

object DistageTestRunner {
  final case class SuiteData(id: SuiteId, meta: SuiteMeta, suiteParallelism: Parallelism)
}

class DistageTestRunner[F[_]](
  reporter: TestReporter,
  logging: TestkitLogging,
  planner: TestPlanner,
  statusConverter: TestStatusConverter,
  timed: TimedActionF[F],
  runnerToF: RunnerToF[F],
  // Only test planning and running parallel envs use runner effect's parallelism capabilities.
  // Parallel suites & tests use parallelism capabilities of their own effect type.
  parTraverseExt: ParTraverseExt[F],
)(implicit
  tagK: TagK[F],
  F: QuasiIO[F],
) {

  def run(tests: Seq[DistageTest[AnyF]]): F[List[EnvResult]] = {
    // We assume that under normal circumstances the code below should never throw.
    // All the exceptions should be converted to values by this time.
    // If it throws, there is a bug which needs to be fixed.
    F.suspendF {
      val id = ScopeId(IzUUID.generateTimeUUID())
      reporter.beginScope(id)

      timed
        .timed(planner.planGroupTests[F](tests, parTraverseExt)(using F))
        .flatMap {
          envs =>
            F.suspendF {
              reportFailedPlanning(id, envs.out.bad, envs.timing)
              reportFailedInvividualPlans(id, envs)

              val toRun = envs.out.good.flatMap(_.envs.toSeq).groupBy(_._1).flatMap(_._2)
              logEnvironmentsInfo(toRun, envs.timing.duration)

              parTraverseExt
                .groupedParTraverse(toRun)(_._1.envExec.parallelEnvs) {
                  case (env, testsTree) =>
                    proceedEnv(id, env, testsTree)
                }.flatMap {
                  result =>
                    F.maybeSuspend {
                      reporter.endScope(id)

                      result
                    }
                }
            }
        }
    }
  }

  private def reportFailedPlanning(id: ScopeId, bad: Seq[(Seq[DistageTest[AnyF]], PlanningFailure)], timing: Timing): Unit = {
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
            reporter.testSetupStatus(id, -1, test.meta, TestStatus.FailedInitialPlanning(failure, asThrowable, timing))
        }
    }
  }

  private def reportFailedInvividualPlans(id: ScopeId, envs: Timed[PlannedTests[AnyF]]): Unit = {
    val failures = envs.out.good.flatMap(_.envs.flatMap(_._2.allFailures))

    failures.foreach {
      ft =>
        reporter.testSetupStatus(id, -1, ft.test.meta, TestStatus.FailedPlanning(ft.timedPlan.timing, ft.timedPlan.out.aggregateErrors))
    }
  }

  protected def proceedEnv[TestF[_]](id: ScopeId, env: PreparedTestEnv[TestF], testsTree: TestTree[TestF]): F[EnvResult] = {
    val PreparedTestEnv(envExec, runtimePlan, runtimeInjector, _) = env

    import envExec.effectType

    val allEnvTests = testsTree.allTests.map(_.test)

    timed.timedLifecycle(runtimeInjector.produceDetailedCustomF[F](runtimePlan)).use {
      maybeRtLocator =>
        maybeRtLocator.foldEither(
          left = (runtimeInstantiationFailure, runtimeInstantiationTiming) =>
            F.maybeSuspend {
              val result = EnvResult.RuntimePlanningFailure(runtimeInstantiationTiming, allEnvTests.map(_.meta), runtimeInstantiationFailure)

              val failure = statusConverter.failRuntimePlanning(result)
              // fail all tests (if an exception reaches here, it must have happened before the runtime was successfully produced)
              allEnvTests.foreach {
                test => reporter.testSetupStatus(id, -1, test.meta, failure)
              }

              result
            },
          right = (runtimeLocator, runtimeInstantiationTiming) =>
            runtimeLocator.run {
              (runner: QuasiIORunner[TestF], testTreeRunner: TestTreeRunner[TestF], logger: IzLogger @Id("distage-testkit")) =>
                logger.info(s"Processing ${allEnvTests.size -> "tests"} using ${effectType.tag -> "monad"}")

                runnerToF
                  .runToF(runner, () => testTreeRunner.traverse(id, 0, runtimeLocator, envExec.parallelEnvs, testsTree))
                  .map[EnvResult](EnvResult.EnvSuccess(runtimeInstantiationTiming, _))
            },
        )
    }
  }

  private def logEnvironmentsInfo(envs: Map[PreparedTestEnv[AnyF], TestTree[AnyF]], duration: FiniteDuration): Unit = {
    val testRunnerLogger = {
      val minimumLogLevel = envs.map(_._1.envExec.logLevel).toSeq.sorted.headOption.getOrElse(Log.Level.Info)
      IzLogger(minimumLogLevel)("phase" -> "testRunner")
    }
    testRunnerLogger.info(s"Test planning took ${duration.toMillis} ...")
    val originalEnvSize = envs.iterator.flatMap(_._2.allTests.map(_.test.environment)).toSet.size
    val memoizationTreesNum = envs.size

    val monads = envs.map(e => new SafeType(e._1.envExec.effectType)).toList.distinct
    testRunnerLogger.info(
      s"Created ${memoizationTreesNum -> "memoization trees"} with ${envs.iterator.flatMap(_._2.allTests).size -> "tests"} using $monads"
    )
    testRunnerLogger.info(s"Merged together ${(originalEnvSize - memoizationTreesNum) -> "raw environments"}")

    envs.foreach {
      case (PreparedTestEnv(_, runtimePlan, _, debugOutput), testTree) =>
        val suites = testTree.allTests.map(_.test.suiteMeta.suiteClassName).toList.distinct
        testRunnerLogger.info(
          s"Memoization environment with ${suites.size -> "suites"} ${testTree.allTests.size -> "tests"} ${testTree.repr -> "suitesMemoizationTree"}"
        )
        testRunnerLogger.log(logging.testkitDebugMessagesLogLevel(debugOutput))(
          s"Effect runtime plan: $runtimePlan"
        )
    }
  }

}
