package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.model.planning.PlanAnalyzer
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import org.scalatest.WordSpec

class ModelTest extends WordSpec {

  "DI Keys" should {
    "support equality checks" in {
      assert(RuntimeDIUniverse.DIKey.get[PlanAnalyzer] == RuntimeDIUniverse.DIKey.get[PlanAnalyzer])
      assert(RuntimeDIUniverse.DIKey.get[PlanAnalyzer].named("xxx") == RuntimeDIUniverse.DIKey.get[PlanAnalyzer].named("xxx"))
    }
  }

  "SafeType" should {
    "support construction from method type signatures" in {
      import RuntimeDIUniverse.u._
      assert(RuntimeDIUniverse.SafeType(typeOf[PlanAnalyzer].member(TermName("resolve")).typeSignature) ==
        RuntimeDIUniverse.SafeType(typeOf[PlanAnalyzer].member(TermName("resolve")).typeSignature))
    }
  }


}
