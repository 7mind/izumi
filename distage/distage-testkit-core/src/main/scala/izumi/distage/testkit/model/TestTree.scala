package izumi.distage.testkit.model

import distage.DIKey
import izumi.distage.model.plan.Plan
import izumi.distage.model.plan.repr.{DIRendering, KeyMinimizer}
import izumi.fundamentals.platform.strings.IzConsoleColors

final case class TestTree[F[_]](
  levelPlan: Plan,
  groups: List[TestGroup[F]],
  nested: List[TestTree[F]],
  parentKeys: Set[DIKey],
) extends IzConsoleColors {
  def repr: String = styled(render(), c.RESET)

  def allTests: Seq[PreparedTest[F]] = {
    thisLevelTests ++ nestedTests
  }

  private def nestedTests: Seq[PreparedTest[F]] = {
    nested.iterator.flatMap(_.allTests).toSeq
  }

  private def thisLevelTests: Seq[PreparedTest[F]] = {
    groups.iterator.flatMap(_.preparedTests).toSeq
  }

  def allFailures: Seq[FailedTest[F]] = {
    thisLevelFailures ++ nestedFailures
  }

  private def nestedFailures: Seq[FailedTest[F]] = {
    nested.iterator.flatMap(_.allFailures).toSeq
  }

  private def thisLevelFailures: Seq[FailedTest[F]] = {
    groups.iterator.flatMap(_.failedTests).toSeq
  }

  override protected def colorsEnabled(): Boolean = DIRendering.colorsEnabled

  private def render(level: Int = 0, suitePad: String = "", levelPad: String = ""): String = {
    val memoizationRoots = levelPlan.keys
    val levelInfo = if (levelPlan.keys.nonEmpty) {
      val minimizer = KeyMinimizer(memoizationRoots, colorsEnabled())
      memoizationRoots.iterator.map(minimizer.renderKey).mkString("[ ", ", ", " ]")
    } else {
      "ø"
    }
    val currentLevelPad = {
      val emptyStep = if (suitePad.isEmpty) "" else s"\n${suitePad.dropRight(5)}║"
      s"$emptyStep\n$levelPad╗ [L$level; ${thisLevelTests.size}T + ${nestedTests.size}I] roots: $levelInfo"
    }

    val str = {
      val testIds = groups.toList.flatMap(_.preparedTests.map(_.test.suiteMeta.suiteName)).distinct.sorted.map(t => s"$suitePad╠══* $t")

      if (testIds.nonEmpty) s"$currentLevelPad\n${testIds.mkString("\n")}" else currentLevelPad
    }

    val updatedLevelPad: String = levelPad.replaceAll("╠════$", "║    ").replaceAll("╚════$", "     ")

    nested.zipWithIndex.foldLeft(str) {
      case (acc, (nextTree, i)) =>
        val isLastChild = nested.size == i + 1
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
