package izumi.distage.testkit.runner.impl

import distage.{Injector, Locator, TagK}
import izumi.distage.testkit.model.*
import izumi.distage.testkit.runner.api.TestReporter
import izumi.distage.testkit.runner.impl.services.{ExtParTraverse, TestStatusConverter, TimedActionF}
import izumi.functional.quasi.QuasiIO
import izumi.functional.quasi.QuasiIO.syntax.*

trait TestTreeRunner[F[_]] {
  def traverse(
    id: ScopeId,
    depth: Int,
    parent: Locator,
    tree: TestTree[F],
  ): F[List[GroupResult]]
}

object TestTreeRunner {

  class TestTreeRunnerImpl[F[_]: TagK](
    reporter: TestReporter,
    statusConverter: TestStatusConverter,
    timed: TimedActionF[F],
    runner: IndividualTestRunner[F],
    extParTraverse: ExtParTraverse[F],
  )(implicit F: QuasiIO[F]
  ) extends TestTreeRunner[F] {

    override def traverse(
      id: ScopeId,
      depth: Int,
      parent: Locator,
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
                for {
                  results <- proceedMemoizationLevel(id, depth, levelLocator, tree.groups)
                  subResults <- F.traverse(tree.nested)(subTree => traverse(id, depth + 1, levelLocator, subTree))
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
    ): F[List[IndividualTestResult]] = {
      val testsBySuite = levelGroups.flatMap {
        group =>
          group.preparedTests.groupBy {
            preparedTest =>
              val testId = preparedTest.test.meta.test.id
              val parallelLevel = preparedTest.test.environment.parallelSuites
              DistageTestRunner.SuiteData(testId.suite, preparedTest.test.suiteMeta, parallelLevel)
          }
      }
      // now we are ready to run each individual test
      // note: scheduling here is custom also and tests may automatically run in parallel for any non-trivial monad
      // we assume that individual tests within a suite can't have different values of `parallelSuites`
      // (because of TestConfig structure & that difference even if happens wouldn't be actionable at the level of suites anyway)
      extParTraverse
        .groupedParTraverse(testsBySuite)(_._1.suiteParallelism) {
          case (suiteData, preparedTests) =>
            F.bracket(
              acquire = F.maybeSuspend {
                reporter.beginLevel(id, depth, suiteData.meta)
              }
            )(release =
              _ =>
                F.maybeSuspend {
                  println(s"AAAAA\nAAAAA : $id = $depth = ${suiteData.meta}")
                  reporter.endLevel(id, depth, suiteData.meta)
                }
            ) {
              _ =>
                extParTraverse.groupedParTraverse(preparedTests)(_.test.environment.parallelTests) {
                  test => runner.proceedTest(id, depth, deepestSharedLocator, test)
                }
            }
        }.map(_.flatten)
    }
  }
}
