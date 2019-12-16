package izumi.distage.impl

import izumi.distage.model.planning.PlanAnalyzer
import distage._
import org.scalatest.WordSpec

class ModelTest extends WordSpec {

  "DI Keys" should {
    "support equality checks" in {
      assert(DIKey.get[PlanAnalyzer] == DIKey.get[PlanAnalyzer])
      assert(DIKey.get[PlanAnalyzer].named("xxx") == DIKey.get[PlanAnalyzer].named("xxx"))
    }
  }

}
