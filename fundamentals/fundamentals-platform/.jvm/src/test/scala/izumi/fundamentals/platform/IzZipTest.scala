package izumi.fundamentals.platform

import java.nio.file.Paths

import izumi.fundamentals.platform.files.IzZip
import izumi.fundamentals.platform.jvm.IzJvm
import org.scalatest.wordspec.AnyWordSpec

class IzZipTest extends AnyWordSpec {

  "zip tools" should {
    "be able to find files in jars" in {
      val files = IzJvm.safeClasspathSeq().map(p => Paths.get(p).toFile)

      for (_ <- 1 to 2) {
        val maybeObjContent = IzZip.findInZips(files, p => p.toString == Paths.get("/scala/Predef.class").toString)
        assert(maybeObjContent.headOption.exists(_._2.nonEmpty))
      }
    }
  }

}
