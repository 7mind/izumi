package izumi.fundamentals.platform

import izumi.fundamentals.platform.os.{IzOs, OsType}
import izumi.fundamentals.testkit.AnyWordSpec

class IzOsTest extends AnyWordSpec {

  "OS tools" should {
    "detect OS version" in {
      assert(IzOs.osType != OsType.Unknown)
    }
  }
}
