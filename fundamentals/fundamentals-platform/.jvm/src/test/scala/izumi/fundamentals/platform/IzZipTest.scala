package izumi.fundamentals.platform

import java.io.File

import izumi.fundamentals.platform.files.IzZip
import izumi.fundamentals.platform.jvm.IzJvm
import org.scalatest.wordspec.AnyWordSpec

class IzZipTest extends AnyWordSpec {

  "zip tools" should {
    "be able to find files in jars" in {
      val files = IzJvm.safeClasspathSeq().map(p => new File(p))

      for (_ <- 1 to 2) {
        // Zip filesystem paths always use forward slashes, so compare with string directly
        val maybeObjContent = IzZip.findInZips(files, p => p.toString == "/scala/Predef.class")
        assert(maybeObjContent.headOption.exists(_._2.nonEmpty))
      }
    }
  }

}
