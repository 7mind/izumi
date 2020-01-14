package izumi.distage.impl

import izumi.distage.model.planning.PlanAnalyzer
import distage._
import org.scalatest.wordspec.AnyWordSpec

class ModelTest extends AnyWordSpec {

  "DI Keys" should {
    "support equality checks" in {
      assert(DIKey.get[PlanAnalyzer] == DIKey.get[PlanAnalyzer])
      assert(DIKey.get[PlanAnalyzer].named("xxx") == DIKey.get[PlanAnalyzer].named("xxx"))
    }
  }

}
