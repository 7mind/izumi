package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.model.planning.PlanResolver
import com.github.pshirshov.izumi.distage.model.references.DIKey
import org.scalatest.WordSpec

class ModelTest extends WordSpec {

  "DI Keys" should {
    "support equality checks" in {
      assert(DIKey.get[PlanResolver] == DIKey.get[PlanResolver])
      assert(DIKey.get[PlanResolver].named("xxx") == DIKey.get[PlanResolver].named("xxx"))
    }
  }


}
