package izumi.distage.testkit.docker

import com.github.dockerjava.api.DockerClient
import izumi.distage.docker.impl.{ContainerFileArchive, CopyFilesHook}
import izumi.distage.docker.model.Docker.{ContainerFile, ContainerId}
import izumi.distage.docker.model.DockerFailureException
import izumi.fundamentals.platform.files.IzFiles
import org.scalatest.wordspec.AnyWordSpec

import java.lang.reflect.{InvocationHandler, Method, Proxy}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.util.Using

final class CopyFilesHookTest extends AnyWordSpec {

  private def withHostFiles[A](test: (Path, Path) => A): A = {
    val root = Files.createTempDirectory("copy-files-hook")
    try {
      val first = root.resolve("first.txt")
      val second = root.resolve("second.txt")
      Files.write(first, "first".getBytes(StandardCharsets.UTF_8))
      Files.write(second, "second".getBytes(StandardCharsets.UTF_8))
      test(first, second)
    } finally {
      IzFiles.erase(root)
    }
  }

  private val untouchableClient: DockerClient = Proxy
    .newProxyInstance(
      classOf[DockerClient].getClassLoader,
      Array(classOf[DockerClient]),
      new InvocationHandler {
        override def invoke(proxy: Any, method: Method, args: Array[AnyRef]): AnyRef = {
          throw new AssertionError(s"the docker client must not be used, called ${method.getName}")
        }
      },
    ).asInstanceOf[DockerClient]

  "ContainerFileArchive.stage" should {
    "stage one archive per file with the digest used for reuse, and delete them on close" in withHostFiles {
      (first, second) =>
        val files = List(ContainerFile(first, "/opt/first.txt"), ContainerFile(second, "/opt/second.txt"))
        val staged = ContainerFileArchive.stage(files)
        val archives = staged.archives
        try {
          assert(archives.size == 2)
          assert(archives.forall(Files.size(_) > 0))
          assert(staged.digest == ContainerFileArchive.digest(files))
        } finally {
          staged.close()
        }
        assert(archives.forall(Files.notExists(_)))
    }

    "fail naming the host path and leave no staged archives when a file cannot be archived" in withHostFiles {
      (first, _) =>
        val missing = first.resolveSibling("missing.txt")
        val stagingDirectory = Files.createDirectory(first.resolveSibling("staging"))
        val failure = intercept[DockerFailureException] {
          ContainerFileArchive.stage(List(ContainerFile(first, "/opt/first.txt"), ContainerFile(missing, "/opt/missing.txt")), stagingDirectory)
        }
        assert(failure.getMessage.contains(missing.toString))
        assert(Using.resource(Files.list(stagingDirectory))(_.count()) == 0L)
    }
  }

  "CopyFilesHook" should {
    "refuse to copy files that changed after the reuse key was computed" in withHostFiles {
      (first, _) =>
        val hook = new CopyFilesHook(List(ContainerFile(first, "/opt/first.txt")))
        val reuseKey = hook.reuseKey
        Files.write(first, "changed".getBytes(StandardCharsets.UTF_8))
        val failure = intercept[IllegalStateException](hook.afterCreate(untouchableClient, ContainerId("unused")))
        assert(failure.getMessage.contains("changed after the container's reuse digest was computed"))
        assert(hook.reuseKey == reuseKey)
    }
  }
}
