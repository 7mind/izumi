package izumi.fundamentals.platform

import izumi.fundamentals.platform.os.{IzOs, OsType}
import org.scalatest.wordspec.AnyWordSpec

class IzOsTest extends AnyWordSpec {

  "OS tools" should {
    "detect OS version" in {
      assert(IzOs.osType != OsType.Unknown)
    }
  }
}


