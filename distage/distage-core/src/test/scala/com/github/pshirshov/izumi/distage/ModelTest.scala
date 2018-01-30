package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.model.planning.PlanResolver
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeUniverse
import org.scalatest.WordSpec

class ModelTest extends WordSpec {

  "DI Keys" should {
    "support equality checks" in {
      assert(RuntimeUniverse.DIKey.get[PlanResolver] == RuntimeUniverse.DIKey.get[PlanResolver])
      assert(RuntimeUniverse.DIKey.get[PlanResolver].named("xxx") == RuntimeUniverse.DIKey.get[PlanResolver].named("xxx"))
    }
  }

  "SafeType" should {
    "support construction from method type signatures" in {
      import RuntimeUniverse.u._
      assert(RuntimeUniverse.SafeType(typeOf[PlanResolver].member(TermName("resolve")).typeSignature) ==
        RuntimeUniverse.SafeType(typeOf[PlanResolver].member(TermName("resolve")).typeSignature))
    }
  }


}
