package izumi.fundamentals.platform

import izumi.fundamentals.platform.resources.IzManifest
import org.scalatest.wordspec.AnyWordSpec

class IzManifestTest extends AnyWordSpec {

  "Manifest reader" should {
    "support classpath manifest loading" in {
      val maybeMf = IzManifest.manifestCl("test.mf").map(IzManifest.read)
      assert(maybeMf.isDefined)
      val mf = maybeMf.get
      assert(mf.version.version.nonEmpty)
      assert(mf.git.revision.nonEmpty)
      assert(mf.git.branch.nonEmpty)
      assert(mf.git.repoClean)
      assert(mf.build.user.nonEmpty)
      assert(mf.build.jdk.nonEmpty)
      assert(mf.build.timestamp.getYear == 2018)
    }

    "support jar manifest loading" in {
      val maybeMf = IzManifest.manifest[AnyWordSpec]().map(IzManifest.read)
      assert(maybeMf.isDefined)
      val mf = maybeMf.get
      assert(mf.version.version.length > 2)
    }
  }


}
