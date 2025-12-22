package izumi.distage.testkit.runner.impl

import distage.{Injector, Locator, TagK}
import izumi.distage.testkit.model.*
import izumi.distage.testkit.model.TestConfig.Parallelism
import izumi.distage.testkit.runner.api.TestReporter
import izumi.distage.testkit.runner.impl.services.{ParTraverseExt, TestStatusConverter, TimedActionF}
import izumi.functional.quasi.QuasiIO
import izumi.functional.quasi.QuasiIO.syntax.*

trait TestTreeRunner[F[_]] {
  def traverse(
    id: ScopeId,
    depth: Int,
    parent: Locator,
    levelParallelism: Parallelism,
    tree: TestTree[F],
  ): F[List[GroupResult]]
}

object TestTreeRunner {

  class TestTreeRunnerImpl[F[_]: TagK](
    reporter: TestReporter,
    statusConverter: TestStatusConverter,
    timed: TimedActionF[F],
    runner: IndividualTestRunner[F],
    parTraverseExt: ParTraverseExt[F],
  )(implicit F: QuasiIO[F]
  ) extends TestTreeRunner[F] {

    override def traverse(
      id: ScopeId,
      depth: Int,
      parent: Locator,
      levelParallelism: Parallelism,
      tree: TestTree[F],
    ): F[List[GroupResult]] = {
      timed.timedLifecycle(Injector.inherit(parent).produceDetailedCustomF[F](tree.levelPlan)).use {
        maybeLocator =>
          maybeLocator.foldEither(
            {
              case (levelInstantiationFailure, levelInstantiationTiming) =>
                F.maybeSuspend {
                  val all = tree.allTests.map(_.test)
                  val result = GroupResult.EnvLevelFailure(all.map(_.meta), levelInstantiationFailure, levelInstantiationTiming)
                  val failure = statusConverter.failLevelInstantiation(result)
                  all.foreach(test => reporter.testStatus(id, depth, test.meta, failure))
                  List(result)
                }
            },
            {
              case (levelLocator, levelInstantiationTiming) =>
                parTraverseExt
                  .configuredParTraverse(levelParallelism)(
                    List(
                      proceedMemoizationLevel(id, depth, levelLocator, tree.groups)
                        .map(results => List[GroupResult](GroupResult.GroupSuccess(results, levelInstantiationTiming))),
                      parTraverseExt
                        .groupedParTraverse(tree.nested)(_ => levelParallelism)(subTree => traverse(id, depth + 1, levelLocator, levelParallelism, subTree))
                        .map(_.flatten),
                    )
                  )(identity).map(_.flatten)
            },
          )
      }
    }

    private def proceedMemoizationLevel(
      id: ScopeId,
      depth: Int,
      deepestSharedLocator: Locator,
      levelGroups: List[TestGroup[F]],
    ): F[List[IndividualTestResult]] = {
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
        acquire = F.maybeSuspend(reporter.beginLevel(id, depth, suiteMetas))
      )(release = _ => F.maybeSuspend(reporter.endLevel(id, depth, suiteMetas))) {
        _ =>
          // now we are ready to run each individual test
          // note: scheduling here is custom also and tests may automatically run in parallel for any non-trivial monad
          // we assume that individual tests within a suite can't have different values of `parallelSuites`
          // (because of TestConfig structure & that difference even if happens wouldn't be actionable at the level of suites anyway)
          parTraverseExt
            .groupedParTraverse(testsBySuite)(_._1._2) {
              case ((suiteMeta, _), preparedTests) =>
                F.bracket(
                  acquire = F.maybeSuspend(reporter.beginSuite(id, depth, suiteMeta))
                )(release = _ => F.maybeSuspend(reporter.endSuite(id, depth, suiteMeta))) {
                  _ =>
                    parTraverseExt.groupedParTraverse(preparedTests)(_.test.environment.parallelTests) {
                      test => runner.proceedTest(id, depth, deepestSharedLocator, test)
                    }
                }
            }.map(_.flatten)
      }
    }
  }
}
