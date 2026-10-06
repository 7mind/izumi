package izumi.fundamentals.platform.resources

import io.github.classgraph.ClassGraph
import izumi.fundamentals.platform.files.IzFiles
import izumi.distage.testkit.runner.spec.Assertion
import izumi.fundamentals.testkit.AnyWordSpec

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

final class PortableResourceConflictTest extends AnyWordSpec {
  private final val resourceDirectory = "conflict-test"
  private final val resourcePath = s"$resourceDirectory/resource.txt"

  private object Scanner extends PortableResourceBase {
    def scan(classpath: Seq[Path]): Either[String, Seq[(String, String)]] = {
      extractResourceContents(resourceDirectory, new ClassGraph().overrideClasspath(classpath.map(_.toString)*))
    }
  }

  private def withClasspathEntries(contents: String*)(test: Seq[Path] => Assertion): Assertion = {
    val root = Files.createTempDirectory("portable-resource-conflict")
    try {
      val entries = contents.zipWithIndex.map {
        case (content, index) =>
          val entry = root.resolve(s"entry-$index")
          val file = entry.resolve(resourcePath)
          Files.createDirectories(file.getParent)
          Files.write(file, content.getBytes(StandardCharsets.UTF_8))
          entry
      }
      test(entries)
    } finally {
      IzFiles.erase(root)
    }
  }

  "embedResources classpath scanning" should {
    "fail naming the path and both classpath entries when same-path resources differ" in withClasspathEntries("first", "second") {
      entries =>
        Scanner.scan(entries) match {
          case Left(message) =>
            assert(message.contains(s"$resourcePath differs between"))
            assert(entries.forall(entry => message.contains(entry.toUri.toString.stripSuffix("/"))), message)
          case Right(found) =>
            fail(s"expected a conflict, found $found")
        }
    }

    "keep one copy of identical same-path resources" in withClasspathEntries("same", "same") {
      entries =>
        assert(Scanner.scan(entries) == Right(Seq(resourcePath -> "same")))
    }
  }
}
