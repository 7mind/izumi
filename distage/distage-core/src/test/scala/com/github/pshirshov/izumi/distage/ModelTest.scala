package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.model.planning.PlanResolver
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import org.scalatest.WordSpec

class ModelTest extends WordSpec {

  "DI Keys" should {
    "support equality checks" in {
      assert(RuntimeDIUniverse.DIKey.get[PlanResolver] == RuntimeDIUniverse.DIKey.get[PlanResolver])
      assert(RuntimeDIUniverse.DIKey.get[PlanResolver].named("xxx") == RuntimeDIUniverse.DIKey.get[PlanResolver].named("xxx"))
    }
  }

  "SafeType" should {
    "support construction from method type signatures" in {
      import RuntimeDIUniverse.u._
      assert(RuntimeDIUniverse.SafeType(typeOf[PlanResolver].member(TermName("resolve")).typeSignature) ==
        RuntimeDIUniverse.SafeType(typeOf[PlanResolver].member(TermName("resolve")).typeSignature))
    }
  }


}
