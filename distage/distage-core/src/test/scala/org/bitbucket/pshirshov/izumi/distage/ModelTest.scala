package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.model.DIKey
import com.github.pshirshov.izumi.distage.planning.PlanResolver
import org.scalatest.WordSpec

class ModelTest extends WordSpec {

  "DI Keys" should {
    "support equality checks" in {
      assert(DIKey.get[PlanResolver] == DIKey.get[PlanResolver])
      assert(DIKey.get[PlanResolver].named("xxx") == DIKey.get[PlanResolver].named("xxx"))
    }
  }


}
