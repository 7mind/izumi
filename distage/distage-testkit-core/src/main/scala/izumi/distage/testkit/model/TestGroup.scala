package izumi.distage.testkit.model

import distage.{Activation, Module, Plan}
import izumi.distage.model.reflection.DIKey

final case class PreparedTest2[F[_]](
  test: DistageTest[F],
  appModule: Module,
  testPlan: Plan,
  activation: Activation,
  newRoots: Set[DIKey],
  newAppModule: Module,
)
final case class TestGroup[F[_]](preparedTests: List[PreparedTest2[F]], strengthenedKeys: Set[DIKey])
