package izumi.fundamentals.platform

import izumi.fundamentals.platform.resources.IzResources
import org.scalatest.wordspec.AnyWordSpec

import java.net.URLClassLoader
import java.nio.charset.StandardCharsets
import java.nio.file.{FileSystems, Files}
import scala.jdk.CollectionConverters.*

class IzResourcesTest extends AnyWordSpec {
  private val resources = IzResources(getClass)

  "IzResources.materialize" should {
    "return the filesystem path of a directory-backed resource as is" in {
      val root = Files.createTempDirectory("izumi-test-classpath")
      val file = root.resolve("nested").resolve("resource.txt")
      Files.createDirectories(file.getParent)
      Files.write(file, "directory-backed".getBytes(StandardCharsets.UTF_8))
      Seq(root, file.getParent, file).foreach(_.toFile.deleteOnExit())
      val loader = new URLClassLoader(Array(root.toUri.toURL), null)
      try {
        val directoryResources = IzResources(loader)
        assert(directoryResources.materialize("nested/resource.txt", "izumi-test").contains(file))
        assert(directoryResources.materialize("nested", "izumi-test").contains(file.getParent))
      } finally {
        loader.close()
      }
    }

    "extract a jar-packaged file" in {
      val path = resources.materialize("org/scalatest/ScalaTestBundle.properties", "izumi-test").get
      assert(path.getFileSystem == FileSystems.getDefault)
      assert(Files.isRegularFile(path))
      assert(path.getFileName.toString.endsWith("ScalaTestBundle.properties"))
      val expected = getClass.getClassLoader.getResourceAsStream("org/scalatest/ScalaTestBundle.properties").readAllBytes()
      assert(Files.readAllBytes(path).toSeq == expected.toSeq)
    }

    "extract a jar-packaged file again once its filesystem is already open" in {
      val first = resources.materialize("org/scalatest/ScalaTestBundle.properties", "izumi-test").get
      val second = resources.materialize("org/scalatest/ScalaTestBundle.properties", "izumi-test").get
      assert(first.getFileSystem == FileSystems.getDefault)
      assert(second.getFileSystem == FileSystems.getDefault)
      assert(Files.readAllBytes(first).toSeq == Files.readAllBytes(second).toSeq)
    }

    "extract a jar-packaged directory" in {
      val path = resources.materialize("scala/annotation/meta", "izumi-test").get
      assert(path.getFileSystem == FileSystems.getDefault)
      assert(Files.isDirectory(path))
      val entries = Files.list(path).iterator().asScala.map(_.getFileName.toString).toList
      assert(entries.contains("companionClass.class"))
    }

    "return None for a missing resource" in {
      assert(resources.materialize("no/such/resource", "izumi-test").isEmpty)
    }
  }
}
