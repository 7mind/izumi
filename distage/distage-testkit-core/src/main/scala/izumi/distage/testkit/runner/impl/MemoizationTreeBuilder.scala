package izumi.distage.testkit.runner.impl

import distage.DIKey
import izumi.distage.model.plan.Plan
import izumi.distage.testkit.model.{PreparedTest2, TestGroup, TestTree}
import izumi.distage.testkit.runner.impl.TestPlanner.PackedEnv

import scala.annotation.tailrec
import scala.collection.concurrent.TrieMap
import scala.collection.mutable.ArrayBuffer

private[impl] final class MemoizationTreeBuilder[F[_]] private (plan: Plan) {
  private[this] val children = TrieMap.empty[Plan, MemoizationTreeBuilder[F]]
  private[this] val groups = ArrayBuffer.empty[PackedEnv[F]]

  private def toImmutable(levelKeys: Set[DIKey]): TestTree[F] = {
    val children1 = children.map(_._2.toImmutable(levelKeys ++ plan.keys)).toList

    val levelGroups = groups.map {
      env =>
        val tests = env.preparedTests.map {
          t =>
            val allSharedKeys = levelKeys ++ t.testPlan.keys
            val newAppModule = t.appModule.drop(allSharedKeys)
            val newRoots = t.testPlan.keys -- allSharedKeys ++ env.strengthenedKeys.intersect(newAppModule.keys)
            
            PreparedTest2(
              t.test,
              t.appModule,
              t.testPlan,
              t.activation,
              newRoots,
              newAppModule,
            )
        }
        TestGroup(tests.toList, env.strengthenedKeys)
    }.toList

    TestTree(plan, levelGroups, children1, levelKeys)
  }

  @tailrec private def addGroupByPath(path: List[Plan], env: PackedEnv[F]): Unit = {
    path match {
      case Nil =>
        groups.synchronized(groups.append(env))
        ()
      case node :: tail =>
        val childTree = children.synchronized(children.getOrElseUpdate(node, new MemoizationTreeBuilder[F](node)))
        childTree.addGroupByPath(tail, env)
    }
  }
}

object MemoizationTreeBuilder {

  def build[F[_]](iterator: Iterable[PackedEnv[F]]): TestTree[F] = {
    val tree = new MemoizationTreeBuilder[F](Plan.empty)
    // usually, we have a small amount of levels, so parallel executions make only worse here
    iterator.foreach {
      env =>
        val plans = env.memoizationPlanTree.filter(_.plan.meta.nodes.nonEmpty)
        tree.addGroupByPath(plans, env)
    }
    tree.toImmutable(Set.empty)
  }

}
