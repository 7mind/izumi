package izumi.distage.testkit.runner.impl

import distage.{DIKey, Identity, Planner, PlannerInput}
import izumi.distage.model.plan.Plan
import izumi.distage.model.reflection.DIKey.SetElementKey
import izumi.distage.testkit.model.{FailedTest, PreparedTest, TestGroup, TestTree}
import izumi.distage.testkit.runner.impl.TestPlanner.PackedEnv
import izumi.distage.testkit.runner.impl.services.TimedActionF

import scala.annotation.tailrec
import scala.collection.mutable

/**
  * Final test planning happens here.
  * This is the point where we actually apply memoization by removing memoized keys
  */
trait TestTreeBuilder {
  def build[F[_]](planner: Planner, runtimePlan: Plan, packedEnvs: Iterable[PackedEnv[F]]): TestTree[F]
}

object TestTreeBuilder {
  class TestTreeBuilderImpl(
    timedId: TimedActionF[Identity]
  ) extends TestTreeBuilder {

    override def build[F[_]](planner: Planner, runtimePlan: Plan, packedEnvs: Iterable[PackedEnv[F]]): TestTree[F] = {
      val tree = new MemoizationTreeBuilder[F](planner, runtimePlan)
      // usually, we have a small amount of levels, so executing in parallel would only make things worse
      packedEnvs.foreach {
        env =>
          val plans = env.memoizationPlanTree.filter(_.plan.meta.nodes.nonEmpty)
          tree.addGroupByPath(plans, env)
      }
      tree.toImmutable
    }

    final class MemoizationTreeBuilder[F[_]](planner: Planner, levelPlan: Plan) {
      private val children = mutable.HashMap.empty[Plan, MemoizationTreeBuilder[F]]
      private val groups = mutable.ArrayBuffer.empty[PackedEnv[F]]

      def toImmutable: TestTree[F] = {
        toImmutable(Set.empty)
      }

      private def toImmutable(parentKeys: Set[DIKey]): TestTree[F] = {
        val sharedKeysAtThisLevel = parentKeys ++ levelPlan.keys

        val levelGroups = groups.map {
          env =>
            val tests = env.preparedTests.map {
              t =>
                val newAppModule = t.appModule.drop(sharedKeysAtThisLevel)

                // filter strengthened keys to _only_ restrengthen keys that are memoized. Avoid forcibly sharing unmemoized elements of unmemoized sets.
                val filteredStrengthenedKeys = env.strengthenedKeys
                  .intersect(newAppModule.keys.asInstanceOf[Set[SetElementKey]])
                  .filter {
                    case SetElementKey(_, reference, _) => sharedKeysAtThisLevel.contains(reference)
                  }
                val newRoots0 = t.targetKeys -- sharedKeysAtThisLevel
                // restrengthen keys to allow unmemoized sets to contain memoized elements and/or elements of memoized sets (see DistageTestExampleBase tests)
                val newRoots = newRoots0 ++ filteredStrengthenedKeys

                val maybePreparedTest = {
                  for {
                    maybeNewTestPlan <- timedId.timed {
                      if (newRoots.nonEmpty) {
                        /** (1) It's important to remember that .plan() would always return the same result regardless of the parent locator!
                          * (2) The planner here must preserve customizations (bootstrap modules) hence be the same as instantiated in TestPlanner
                          */
                        planner.plan(PlannerInput(newAppModule, newRoots, t.activation))
                      } else {
                        Right(Plan.empty)
                      }
                    }.invert
                  } yield {
                    PreparedTest(
                      t.test,
                      maybeNewTestPlan,
                      newRoots,
                    )
                  }
                }

                (t, maybePreparedTest)
            }

            val goodTests = tests.collect {
              case (_, Right(preparedTest)) => preparedTest
            }.toList

            val badTests = tests.collect {
              case (t, Left(error)) => FailedTest(t.test, error)
            }.toList

            TestGroup(goodTests, badTests, env.strengthenedKeys)
        }.toList

        val children1 = children.synchronized(children.map(_._2.toImmutable(sharedKeysAtThisLevel)).toList)
        TestTree(levelPlan, levelGroups, children1, parentKeys)
      }

      @tailrec def addGroupByPath(path: List[Plan], env: PackedEnv[F]): Unit = {
        path match {
          case Nil =>
            groups.synchronized(groups.append(env))
            ()
          case plan :: tail =>
            val childTree = children.synchronized(children.getOrElseUpdate(plan, new MemoizationTreeBuilder(planner, plan)))
            childTree.addGroupByPath(tail, env)
        }
      }
    }

  }
}
