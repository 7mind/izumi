package izumi.fundamentals.platform

import izumi.fundamentals.platform.files.IzFiles
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.{Files, Paths}
import java.util.concurrent.TimeUnit
import scala.concurrent.Await
import scala.concurrent.duration.Duration

class IzFilesTest extends AnyWordSpec {

  "File tools" should {
    "resolve path entries on nix-like systems" in {
      assert(IzFiles.which("bash").nonEmpty)
    }

    "destroy directories and files" in {
      val dir0 = Files.createTempDirectory("test")
      assert(dir0.toFile.exists())

      IzFiles.erase(dir0.toFile)
      assert(!dir0.toFile.exists())

      val file0 = Files.createTempFile("test", "test")
      assert(file0.toFile.exists())
      IzFiles.erase(file0.toFile)
      assert(!file0.toFile.exists())
    }

    "resolve home directory" in {
      Paths.get(IzFiles.homedir()).toFile.isDirectory
    }

//    "support last modified" in {
//      assert(IzFiles.getLastModifiedRecursively(IzFiles.home().toFile).nonEmpty)
//    }

  }

  import izumi.fundamentals.platform.resources.IzResources

  import scala.concurrent.Future

  "Resource tools" should {
    "support concurrent queries" in {
      // the filesystem logic might occasionally fail during initialization
      // we need to file a JDK bug if we can reliably reproduce this
      import scala.concurrent.ExecutionContext.Implicits.global

      val seq = (0 to 200).map {
        _ =>
          Future(IzResources.getPath("library.properties"))
      }
      val res = Await.result(Future.sequence(seq), Duration.apply(1, TimeUnit.MINUTES))
      assert(res.forall(_.isDefined))
    }
  }

}
