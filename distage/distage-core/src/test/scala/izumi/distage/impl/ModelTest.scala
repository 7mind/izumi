package izumi.distage.impl

import distage._
import org.scalatest.wordspec.AnyWordSpec

class ModelTest extends AnyWordSpec {

  "DI Keys" should {
    "support equality checks" in {
      assert(DIKey.get[ModelTest] == DIKey.get[ModelTest])
      assert(DIKey.get[ModelTest].named("xxx") == DIKey.get[ModelTest].named("xxx"))
    }
  }

}
