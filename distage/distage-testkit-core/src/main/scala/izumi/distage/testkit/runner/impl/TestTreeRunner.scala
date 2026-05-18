package izumi.distage.testkit.runner.impl

import distage.{Injector, Locator, TagKK}
import izumi.distage.testkit.model.*
import izumi.distage.testkit.model.TestConfig.Parallelism
import izumi.distage.testkit.runner.api.TestReporter
import izumi.distage.testkit.runner.impl.services.{ParTraverseExt, TestStatusConverter, TimedActionF}
import izumi.functional.bio.{IO2, Primitives2}

trait TestTreeRunner[F[+_, +_]] {
  def traverse(
    id: ScopeId,
    depth: Int,
    parent: Locator,
    levelParallelism: Parallelism,
    tree: TestTree[F[Throwable, _]],
  ): F[Throwable, List[GroupResult]]
}

object TestTreeRunner {

  class TestTreeRunnerImpl[F[+_, +_]: TagKK](
    reporter: TestReporter,
    statusConverter: TestStatusConverter,
    timed: TimedActionF[F],
    runner: IndividualTestRunner[F],
    parTraverseExt: ParTraverseExt[F],
  )(implicit F: IO2[F],
    FP: Primitives2[F],
  ) extends TestTreeRunner[F] {

    override def traverse(
      id: ScopeId,
      depth: Int,
      parent: Locator,
      levelParallelism: Parallelism,
      tree: TestTree[F[Throwable, _]],
    ): F[Throwable, List[GroupResult]] = {
      timed.timedLifecycle[Throwable, Either[izumi.distage.model.provisioning.PlanInterpreter.FailedProvision, Locator]](Injector.inherit(parent).produceDetailedCustomF[F](tree.levelPlan)).use {
        maybeLocator =>
          maybeLocator.foldEither(
            {
              case (levelInstantiationFailure, levelInstantiationTiming) =>
                F.syncThrowable {
                  val all = tree.allTests.map(_.test)
                  val result = GroupResult.EnvLevelFailure(all.map(_.meta), levelInstantiationFailure, levelInstantiationTiming)
                  val failure = statusConverter.failLevelInstantiation(result)
                  all.foreach(test => reporter.testStatus(id, depth, test.meta, failure))
                  List(result)
                }
            },
            {
              case (levelLocator, levelInstantiationTiming) =>
                F.map(
                  parTraverseExt
                    .configuredParTraverse[Throwable, F[Throwable, List[GroupResult]], List[GroupResult]](levelParallelism)(
                      List(
                        F.map(proceedMemoizationLevel(id, depth, levelLocator, tree.groups))(results => List[GroupResult](GroupResult.GroupSuccess(results, levelInstantiationTiming))),
                        F.map(
                          parTraverseExt
                            .groupedParTraverse[Throwable, TestTree[F[Throwable, _]], List[GroupResult]](tree.nested)(_ => levelParallelism)(subTree => traverse(id, depth + 1, levelLocator, levelParallelism, subTree))
                        )(_.flatten),
                      )
                    )(identity)
                )(_.flatten)
            },
          )
      }
    }

    private def proceedMemoizationLevel(
      id: ScopeId,
      depth: Int,
      deepestSharedLocator: Locator,
      levelGroups: List[TestGroup[F[Throwable, _]]],
    ): F[Throwable, List[IndividualTestResult]] = {
      val testsBySuite = levelGroups.flatMap {
        group =>
          group.preparedTests.groupBy {
            preparedTest =>
              val suiteMeta = preparedTest.test.suiteMeta
              val parallelLevel = preparedTest.test.environment.parallelSuites
              (suiteMeta, parallelLevel)
          }
      }
      val suiteMetas = testsBySuite.map(_._1._1)
      F.bracket(
        acquire = F.syncThrowable(reporter.beginLevel(id, depth, suiteMetas))
      )(release = _ => F.syncThrowable(reporter.endLevel(id, depth, suiteMetas)).orTerminate) {
        _ =>
          // now we are ready to run each individual test
          // note: scheduling here is custom also and tests may automatically run in parallel for any non-trivial monad
          // we assume that individual tests within a suite can't have different values of `parallelSuites`
          // (because of TestConfig structure & that difference even if happens wouldn't be actionable at the level of suites anyway)
          F.map(
            parTraverseExt
              .groupedParTraverse[Throwable, ((SuiteMeta, Parallelism), List[PreparedTest[F[Throwable, _]]]), List[IndividualTestResult]](testsBySuite)(_._1._2) {
                case ((suiteMeta, _), preparedTests) =>
                  F.bracket(
                    acquire = F.syncThrowable(reporter.beginSuite(id, depth, suiteMeta))
                  )(release = _ => F.syncThrowable(reporter.endSuite(id, depth, suiteMeta)).orTerminate) {
                    _ =>
                      parTraverseExt.groupedParTraverse[Throwable, PreparedTest[F[Throwable, _]], IndividualTestResult](preparedTests)(_.test.environment.parallelTests) {
                        test => runner.proceedTest(id, depth, deepestSharedLocator, test)
                      }
                  }
              }
          )(_.flatten)
      }
    }
  }
}
