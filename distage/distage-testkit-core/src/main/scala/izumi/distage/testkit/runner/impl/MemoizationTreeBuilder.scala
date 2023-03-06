package izumi.distage.testkit.runner.impl

import izumi.distage.model.plan.Plan
import izumi.distage.testkit.model.{TestGroup, TestTree}
import izumi.distage.testkit.runner.impl.TestPlanner.PackedEnv

import scala.annotation.tailrec
import scala.collection.concurrent.TrieMap
import scala.collection.mutable.ArrayBuffer

private[impl] final class MemoizationTreeBuilder[F[_]] private (plan: Plan) {
  private[this] val children = TrieMap.empty[Plan, MemoizationTreeBuilder[F]]
  private[this] val groups = ArrayBuffer.empty[TestGroup[F]]

  def toImmutable: TestTree[F] = {
    TestTree(plan, groups.toList, children.map(_._2.toImmutable).toList)
  }

  private def addGroup(group: TestGroup[F]): Unit = {
    groups.synchronized(groups.append(group))
    ()
  }

  @tailrec private def addGroupByPath(path: List[Plan], levelTests: TestGroup[F]): Unit = {
    path match {
      case Nil =>
        addGroup(levelTests)
      case node :: tail =>
        val childTree = children.synchronized(children.getOrElseUpdate(node, new MemoizationTreeBuilder[F](node)))
        childTree.addGroupByPath(tail, levelTests)
    }
  }
}

object MemoizationTreeBuilder {

  def build[F[_]](iterator: Iterable[PackedEnv[F]]): MemoizationTreeBuilder[F] = {
    val tree = new MemoizationTreeBuilder[F](Plan.empty)
    // usually, we have a small amount of levels, so parallel executions make only worse here
    iterator.foreach {
      env =>
        val plans = env.memoizationPlanTree.filter(_.plan.meta.nodes.nonEmpty)
        tree.addGroupByPath(plans, TestGroup(env.preparedTests.toList, env.strengthenedKeys))
    }
    tree
  }
}
