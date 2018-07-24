package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.model.planning.PlanAnalyzer
import distage._
import org.scalatest.WordSpec

class ModelTest extends WordSpec {

  "DI Keys" should {
    "support equality checks" in {
      assert(DIKey.get[PlanAnalyzer] == DIKey.get[PlanAnalyzer])
      assert(DIKey.get[PlanAnalyzer].named("xxx") == DIKey.get[PlanAnalyzer].named("xxx"))
    }
  }

  "SafeType" should {
    "support construction from method type signatures" in {
      import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.u._
      assert(SafeType(typeOf[PlanAnalyzer].member(TermName("resolve")).typeSignature) ==
        SafeType(typeOf[PlanAnalyzer].member(TermName("resolve")).typeSignature))
    }
  }


}
