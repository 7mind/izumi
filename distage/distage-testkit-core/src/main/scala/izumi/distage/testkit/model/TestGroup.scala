package izumi.distage.testkit.model

import distage.{Activation, Module}
import izumi.distage.model.reflection.DIKey

final case class PreparedTest[F[_]](
                                     test: DistageTest[F],
                                     activation: Activation,
                                     roots: Set[DIKey],
                                     module: Module,
)

final case class TestGroup[F[_]](preparedTests: List[PreparedTest[F]], strengthenedKeys: Set[DIKey])
