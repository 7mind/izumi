package izumi.distage.testkit.model

import izumi.distage.model.exceptions.planning.InjectorFailed
import izumi.distage.model.plan.Plan
import izumi.distage.model.reflection.DIKey
import izumi.distage.testkit.runner.impl.services.Timed

final case class PreparedTest[F[_]](
  test: DistageTest[F],
  maybePlan: F[Timed[Either[InjectorFailed, Plan]]],
  roots: Set[DIKey],
)

final case class TestGroup[F[_]](preparedTests: List[PreparedTest[F]], strengthenedKeys: Set[DIKey])
