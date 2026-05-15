package izumi.distage.testkit.runner.impl

import distage.*
import izumi.distage.testkit.model.*
import izumi.distage.testkit.model.TestEnvironment
import izumi.distage.testkit.runner.api.TestReporter
import izumi.distage.testkit.runner.impl.TestPlanner.*
import izumi.distage.testkit.runner.impl.services.*
import izumi.functional.bio.{IO2, Primitives2, UnsafeRun2}
import izumi.fundamentals.platform.language.types.HigherKindedAny.AnyF
import izumi.fundamentals.platform.uuid.IzUUID
import izumi.logstage.api.IzLogger
import logstage.Log

import scala.concurrent.duration.FiniteDuration

class DistageTestRunner[F[+_, +_]](
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
  tagKK: TagKK[F],
  F: IO2[F],
  FP: Primitives2[F],
) {

  def run(tests: Seq[DistageTest[AnyF]]): F[Throwable, List[EnvResult]] = {
    // We assume that under normal circumstances the code below should never throw.
    // All the exceptions should be converted to values by this time.
    // If it throws, there is a bug which needs to be fixed.
    F.suspendThrowable {
      val id = ScopeId(IzUUID.generateTimeUUID())
      reporter.beginScope(id)

      F.flatMap(
        timed
          .timed[Throwable, PlannedTests[AnyF]](planner.planGroupTests[F](tests, parTraverseExt)(using F))
      ) {
        envs =>
          F.suspendThrowable {
            reportFailedPlanning(id, envs.out.bad, envs.timing)
            reportFailedInvividualPlans(id, envs)

            val toRun = envs.out.good.flatMap(_.envs.toSeq).groupBy(_._1).flatMap(_._2)
            logEnvironmentsInfo(toRun, envs.timing.duration)

            F.flatMap(
              parTraverseExt
                .groupedParTraverse[Throwable, (PreparedTestEnv[AnyF], TestTree[AnyF]), EnvResult](toRun)(_._1.envExec.parallelEnvs) {
                  case (env, testsTree) =>
                    proceedEnv(id, env, testsTree)
                }
            ) {
              result =>
                F.syncThrowable {
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

  protected def proceedEnv[TestF[_]](id: ScopeId, env: PreparedTestEnv[TestF], testsTree: TestTree[TestF]): F[Throwable, EnvResult] = {
    val envExec = env.envExec
    val runtimePlan = env.runtimePlan
    val runtimeInjector = env.runtimeInjector

    val allEnvTests = testsTree.allTests.map(_.test)

    timed.timedLifecycle[Throwable, Either[izumi.distage.model.provisioning.PlanInterpreter.FailedProvision, Locator]](runtimeInjector.produceDetailedCustomF[F](runtimePlan)).use {
      maybeRtLocator =>
        maybeRtLocator.foldEither(
          left = (runtimeInstantiationFailure, runtimeInstantiationTiming) =>
            F.syncThrowable {
              val result = EnvResult.RuntimePlanningFailure(runtimeInstantiationTiming, allEnvTests.map(_.meta), runtimeInstantiationFailure)

              val failure = statusConverter.failRuntimePlanning(result)
              // fail all tests (if an exception reaches here, it must have happened before the runtime was successfully produced)
              allEnvTests.foreach {
                test => reporter.testSetupStatus(id, -1, test.meta, failure)
              }

              result
            },
          right = (runtimeLocator, runtimeInstantiationTiming) =>
            runEnvWithLocatorWithTag(id, envExec, runtimeLocator, runtimeInstantiationTiming, allEnvTests.size, testsTree),
        )
    }
  }

  // Reify the test effect type as a concrete bifunctor type parameter `TestBI` so the implicit TagKK
  // captures `envExec.effectType` at value, not at type-symbol level. This decouples DIKey lookup from
  // the path-dependent `envExec.F` symbol.
  private def runEnvWithLocatorWithTag[TestBI[+_, +_]](
    id: ScopeId,
    envExec: TestEnvironment.EnvExecutionParams,
    runtimeLocator: Locator,
    runtimeInstantiationTiming: Timing,
    nTests: Int,
    testsTree: TestTree[?],
  )(implicit
    // Empty placeholder; the caller has to provide the right TagKK at call time. We supply it via the
    // helper-stub trick at use site.
    @scala.annotation.unused dummy: DummyImplicit
  ): F[Throwable, EnvResult] = {
    implicit val tagKKTestBI: TagKK[TestBI] = envExec.effectType.asInstanceOf[TagKK[TestBI]]
    val runner = runtimeLocator.get[UnsafeRun2[TestBI]]
    val testTreeRunner = runtimeLocator.get[TestTreeRunner[TestBI]]
    val logger = runtimeLocator.get[IzLogger]("distage-testkit")
    logger.info(s"Processing ${nTests -> "tests"} using ${envExec.effectType.tag -> "monad"}")
    F.map[Throwable, List[GroupResult], EnvResult](
      runnerToF
        .runToF[TestBI, Throwable, List[GroupResult]](runner, () => testTreeRunner.traverse(id, 0, runtimeLocator, envExec.parallelEnvs, testsTree.asInstanceOf[TestTree[TestBI[Throwable, _]]]))
    )(EnvResult.EnvSuccess(runtimeInstantiationTiming, _))
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
