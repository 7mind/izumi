package izumi.distage.testkit.model

import izumi.distage.model.reflection.DIKey
import izumi.distage.testkit.runner.impl.TestPlanner.PreparedTest

final case class TestGroup[F[_]](preparedTests: List[PreparedTest[F]], strengthenedKeys: Set[DIKey])
