package izumi.distage.testkit.runner.impl

import izumi.distage.model.plan.Plan
import izumi.distage.model.plan.repr.{DIRendering, KeyMinimizer}
import izumi.distage.model.reflection.DIKey
import izumi.distage.testkit.runner.impl.MemoizationTree.TestGroup
import izumi.distage.testkit.runner.impl.TestPlanner.{PackedEnv, PreparedTest}

import scala.annotation.{nowarn, tailrec}
import scala.collection.concurrent.TrieMap
import scala.collection.mutable.ArrayBuffer

/**
  * Structure for creation, storage and traversing over memoization levels.
  * To support the memoization level we should create a memoization tree first, where every node will contain a unique part of the memoization plan.
  * For better performance we are going to use mutable structures. Mutate this tree only in case when you KNOW what you doing.
  * Every change in tree structure may lead to test failed across all childs of the corrupted node.
  */
final class MemoizationTree[F[_]](val plan: Plan) {
  private[this] val children = TrieMap.empty[Plan, MemoizationTree[F]]
  private[this] val groups = ArrayBuffer.empty[TestGroup[F]]

  def getGroups: List[TestGroup[F]] = groups.toList

  def getAllTests: Seq[PreparedTest[F]] = {
    (groups.iterator.flatMap(_.preparedTests) ++ children.iterator.flatMap(_._2.getAllTests)).toSeq
  }

  def addGroup(group: TestGroup[F]): Unit = {
    groups.synchronized(groups.append(group))
    ()
  }

  def add(memoizationTree: MemoizationTree[F]): Unit = {
    children.synchronized(children.put(memoizationTree.plan, memoizationTree))
    ()
  }

  @nowarn("msg=Unused import")
  def next: List[MemoizationTree[F]] = {
    import scala.collection.compat.*
    children.view.map(_._2).toList
  }

  @inline override def toString: String = render()

  @tailrec private def addGroupByPath(path: List[Plan], levelTests: TestGroup[F]): Unit = {
    path match {
      case Nil =>
        addGroup(levelTests)
      case node :: tail =>
        val childTree = children.synchronized(children.getOrElseUpdate(node, new MemoizationTree[F](node)))
        childTree.addGroupByPath(tail, levelTests)
    }
  }

  private def render(level: Int = 0, suitePad: String = "", levelPad: String = ""): String = {
    val memoizationRoots = plan.keys
    val levelInfo = if (plan.keys.nonEmpty) {
      val minimizer = KeyMinimizer(memoizationRoots, DIRendering.colorsEnabled)
      memoizationRoots.iterator.map(minimizer.renderKey).mkString("[ ", ", ", " ]")
    } else {
      "ø"
    }
    val currentLevelPad = {
      val emptyStep = if (suitePad.isEmpty) "" else s"\n${suitePad.dropRight(5)}║"
      s"$emptyStep\n$levelPad╗ [$level] MEMOIZATION ROOTS: $levelInfo"
    }

    val str = {
      val testIds = groups.toList.flatMap(_.preparedTests.map(_.test.suiteMeta.suiteName)).distinct.sorted.map(t => s"$suitePad╠══* $t")

      if (testIds.nonEmpty) s"$currentLevelPad\n${testIds.mkString("\n")}" else currentLevelPad
    }

    val updatedLevelPad: String = levelPad.replaceAll("╠════$", "║    ").replaceAll("╚════$", "     ")

    children.toList.zipWithIndex.foldLeft(str) {
      case (acc, ((_, nextTree), i)) =>
        val isLastChild = children.size == i + 1
        val nextSuitePad = suitePad + (if (isLastChild) "     " else "║    ")
        val nextLevelPad = level match {
          case 0 if isLastChild => "╚════"
          case _ if isLastChild => s"$updatedLevelPad╚════"
          case _ => s"$updatedLevelPad╠════"
        }
        val nextChildStr = nextTree.render(level + 1, nextSuitePad, nextLevelPad)
        s"$acc$nextChildStr"
    }
  }
}

object MemoizationTree {
  final case class TestGroup[F[_]](preparedTests: Iterable[PreparedTest[F]], strengthenedKeys: Set[DIKey])

  def apply[F[_]](iterator: Iterable[PackedEnv[F]]): MemoizationTree[F] = {
    val tree = new MemoizationTree[F](Plan.empty)
    // usually, we have a small amount of levels, so parallel executions make only worse here
    iterator.foreach {
      env =>
        val plans = env.memoizationPlanTree.filter(_.plan.meta.nodes.nonEmpty)
        tree.addGroupByPath(plans, TestGroup(env.preparedTests, env.strengthenedKeys))
    }
    tree
  }
}
