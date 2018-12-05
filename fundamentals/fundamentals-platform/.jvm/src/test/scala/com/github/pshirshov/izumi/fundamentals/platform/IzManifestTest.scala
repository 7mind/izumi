package com.github.pshirshov.izumi.fundamentals.platform

import com.github.pshirshov.izumi.fundamentals.platform.resources.IzManifest
import org.scalatest.WordSpec

class IzManifestTest extends WordSpec {

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
      val maybeMf = IzManifest.manifest[WordSpec]().map(IzManifest.read)
      assert(maybeMf.isDefined)
      val mf = maybeMf.get
      assert(mf.version.version.length > 2)
    }
  }


}
