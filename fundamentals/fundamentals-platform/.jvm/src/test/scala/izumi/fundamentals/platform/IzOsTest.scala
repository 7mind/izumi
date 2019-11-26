package izumi.fundamentals.platform

import izumi.fundamentals.platform.os.{IzOs, OsType}
import org.scalatest.WordSpec

class IzOsTest extends WordSpec {

  "OS tools" should {
    "detect OS version" in {
      assert(IzOs.osType != OsType.Unknown)
    }
  }
}


