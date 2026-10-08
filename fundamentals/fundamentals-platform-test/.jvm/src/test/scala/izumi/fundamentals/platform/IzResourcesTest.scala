package izumi.fundamentals.platform

import izumi.fundamentals.platform.resources.IzResources
import izumi.fundamentals.platform.resources.IzResources.MaterializedResource
import izumi.fundamentals.testkit.AnyWordSpec

import java.net.URLClassLoader
import java.nio.charset.StandardCharsets
import java.nio.file.{FileSystemException, FileSystems, Files, Path, Paths}
import java.util.UUID
import java.util.zip.{ZipEntry, ZipOutputStream}
import scala.jdk.CollectionConverters.*
import scala.util.Using

class IzResourcesTest extends AnyWordSpec {
  private final val UnrepresentableFileNameLength = 300

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
      val resource = extracted("scala/annotation/meta/companionClass.class")
      val path: Path = resource.path
      assert(path.getFileSystem == FileSystems.getDefault)
      assert(path.getFileName.toString.endsWith("companionClass.class"))
      val expected = getClass.getClassLoader.getResourceAsStream("scala/annotation/meta/companionClass.class").readAllBytes()
      assert(Files.readAllBytes(path).toSeq == expected.toSeq)
      resource.close()
      assert(!Files.exists(path))
    }

    "extract a jar-packaged file again once its filesystem is already open" in {
      Using.resources(extracted("scala/annotation/meta/companionClass.class"), extracted("scala/annotation/meta/companionClass.class")) {
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

    "allow closing an extracted resource more than once" in {
      val resource = extracted("scala/annotation/meta/companionClass.class")
      resource.close()
      resource.close()
      assert(!Files.exists(resource.path))
    }

    "delete the partial copy when extracting a jar-packaged directory fails" in {
      val root = Files.createTempDirectory("izumi-test-jar")
      val jar = root.resolve("resources.jar")
      Using.resource(new ZipOutputStream(Files.newOutputStream(jar))) {
        zip =>
          List("partial/", "partial/a.txt", s"partial/b-${"x" * UnrepresentableFileNameLength}.txt").foreach {
            name =>
              zip.putNextEntry(new ZipEntry(name))
              zip.write(name.getBytes(StandardCharsets.UTF_8))
              zip.closeEntry()
          }
      }
      val tempPrefix = s"izumi-test-${UUID.randomUUID()}"
      def leftovers(): List[Path] = {
        Using.resource(Files.list(Paths.get(System.getProperty("java.io.tmpdir"))))(_.iterator().asScala.filter(_.getFileName.toString.startsWith(tempPrefix)).toList)
      }
      try {
        Using.resource(new URLClassLoader(Array(jar.toUri.toURL), null)) {
          loader =>
            assertThrows[FileSystemException](IzResources(loader).materialize("partial", tempPrefix))
            assert(leftovers().isEmpty)
        }
      } finally {
        leftovers().foreach(MaterializedResource.Extracted(_).close())
        MaterializedResource.Extracted(root).close()
      }
    }

    "return None for a missing resource" in {
      assert(resources.materialize("no/such/resource", "izumi-test").isEmpty)
    }
  }
}
