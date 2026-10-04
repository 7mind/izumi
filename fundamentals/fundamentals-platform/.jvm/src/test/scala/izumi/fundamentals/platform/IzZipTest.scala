package izumi.fundamentals.platform

import java.nio.file.{Files, Paths}

import izumi.fundamentals.platform.files.IzZip
import izumi.fundamentals.platform.resources.IzResources
import izumi.fundamentals.platform.resources.IzResources.LoadablePathReference
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

    "leave a zip filesystem that other code holds open" in {
      val held = IzResources.getPath("scala/Predef.class") match {
        case Some(LoadablePathReference(path, _)) => path
        case other => fail(s"expected scala/Predef.class inside a jar, got $other")
      }
      val scalaLibraryJar = Paths.get(held.getFileSystem.toString).toFile
      IzZip.findInZips(Seq(scalaLibraryJar), p => p.toString == Paths.get("/scala/Predef.class").toString)
      assert(Files.size(held) > 0)
    }
  }

}
