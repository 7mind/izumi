package com.github.pshirshov.izumi.fundamentals.platform

import com.github.pshirshov.izumi.fundamentals.platform.resources.IzumiManifest
import org.scalatest.WordSpec

class IzManifestTest extends WordSpec {

  "Manifest reader" should {
    "support manifest loading" in {
      val maybeData = IzumiManifest.read("test.mf")
      assert(maybeData.isDefined)
      val data = maybeData.get
      assert(data.app.isDefined)
      assert(data.buildStatus.isDefined)
      assert(data.git.isDefined)

      val app = data.app.get
      val git = data.git.get
      val buildStatus = data.buildStatus.get

      assert(app.version.nonEmpty)
      assert(git.revision.nonEmpty)
      assert(git.branch.nonEmpty)
      assert(git.repoClean)
      assert(buildStatus.buildBy.nonEmpty)
      assert(buildStatus.buildJdk.nonEmpty)
      assert(buildStatus.buildTimestamp.getYear == 2018)
    }
  }


}
