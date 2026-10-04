package izumi.fundamentals.platform

import izumi.fundamentals.platform.resources.IzResources
import izumi.fundamentals.platform.resources.IzResources.MaterializedResource
import org.scalatest.wordspec.AnyWordSpec

import java.net.URLClassLoader
import java.nio.charset.StandardCharsets
import java.nio.file.{FileSystems, Files, Path}
import scala.jdk.CollectionConverters.*
import scala.util.Using

class IzResourcesTest extends AnyWordSpec {
  private val resources = IzResources(getClass)

  private def extracted(resPath: String): MaterializedResource.Extracted = {
    resources.materialize(resPath, "izumi-test") match {
      case Some(e: MaterializedResource.Extracted) => e
      case other => fail(s"expected an extracted copy of $resPath, got $other")
    }
  }

  "IzResources.materialize" should {
    "return a directory-backed resource in place" in {
      val root = Files.createTempDirectory("izumi-test-classpath")
      val file = root.resolve("nested").resolve("resource.txt")
      Files.createDirectories(file.getParent)
      Files.write(file, "directory-backed".getBytes(StandardCharsets.UTF_8))
      try {
        Using.resource(new URLClassLoader(Array(root.toUri.toURL), null)) {
          loader =>
            val directoryResources = IzResources(loader)
            assert(directoryResources.materialize("nested/resource.txt", "izumi-test").contains(MaterializedResource.InPlace(file)))
            assert(directoryResources.materialize("nested", "izumi-test").contains(MaterializedResource.InPlace(file.getParent)))
            directoryResources.materialize("nested", "izumi-test").foreach(_.close())
            assert(Files.exists(file))
        }
      } finally {
        MaterializedResource.Extracted(root).close()
      }
    }

    "extract a jar-packaged file and delete it on close" in {
      val resource = extracted("org/scalatest/ScalaTestBundle.properties")
      val path: Path = resource.path
      assert(path.getFileSystem == FileSystems.getDefault)
      assert(path.getFileName.toString.endsWith("ScalaTestBundle.properties"))
      val expected = getClass.getClassLoader.getResourceAsStream("org/scalatest/ScalaTestBundle.properties").readAllBytes()
      assert(Files.readAllBytes(path).toSeq == expected.toSeq)
      resource.close()
      assert(!Files.exists(path))
    }

    "extract a jar-packaged file again once its filesystem is already open" in {
      Using.resources(extracted("org/scalatest/ScalaTestBundle.properties"), extracted("org/scalatest/ScalaTestBundle.properties")) {
        (first, second) =>
          assert(first.path != second.path)
          assert(Files.readAllBytes(first.path).toSeq == Files.readAllBytes(second.path).toSeq)
      }
    }

    "extract a jar-packaged directory and delete the whole tree on close" in {
      val resource = extracted("scala/annotation/meta")
      val path = resource.path
      assert(Files.isDirectory(path))
      val entries = Using.resource(Files.list(path))(_.iterator().asScala.map(_.getFileName.toString).toList)
      assert(entries.contains("companionClass.class"))
      resource.close()
      assert(!Files.exists(path))
    }

    "return None for a missing resource" in {
      assert(resources.materialize("no/such/resource", "izumi-test").isEmpty)
    }
  }
}
