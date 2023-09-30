package izumi.distage.testkit.model

import izumi.distage.model.definition.errors.DIError
import izumi.distage.model.plan.Plan
import izumi.distage.model.reflection.DIKey
import izumi.distage.testkit.runner.impl.services.Timed
import izumi.fundamentals.collections.nonempty.NEList

final case class PreparedTest[F[_]](
  test: DistageTest[F],
  timedPlan: Timed[Plan],
  roots: Set[DIKey],
)

final case class FailedTest[F[_]](
  test: DistageTest[F],
  timedPlan: Timed[NEList[DIError]],
)

final case class TestGroup[F[_]](
  preparedTests: List[PreparedTest[F]],
  failedTests: List[FailedTest[F]],
  strengthenedKeys: Set[DIKey],
)
