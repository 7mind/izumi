package izumi.distage.testkit.docker

import distage.ModuleDef
import izumi.distage.docker.ContainerDef
import izumi.distage.docker.healthcheck.ContainerHealthCheck
import izumi.distage.docker.impl.{ContainerResource, DockerClientWrapper}
import izumi.distage.docker.model.Docker.{ContainerConfig, ContainerFile, DockerReusePolicy}
import izumi.distage.docker.model.DockerFailureException
import izumi.distage.testkit.docker.ContainerFilesTest.*
import izumi.distage.testkit.model.TestConfig
import izumi.distage.testkit.runner.spec.Spec2
import izumi.fundamentals.assertions.bio.BIOAssertionSuspension.*
import izumi.fundamentals.platform.files.IzFiles
import zio.{IO, Scope, Task, ZIO}

import java.nio.charset.StandardCharsets
import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.{Files, Path, Paths}
import java.util.UUID
import scala.jdk.CollectionConverters.*

object ContainerFilesTest {
  final val copiedDirectory = "/opt/distage/copied"
  final val copiedFile = "/opt/distage/single.txt"
  final val reusedFile = "/opt/distage/reused.txt"
  final val testLabel = "distage.test"
  final val keepRunningSeconds = "3600"

  object VerifyingContainer extends ContainerDef {
    override def config: Config = {
      Config(
        registry = Some("public.ecr.aws"),
        image = "docker/library/alpine:3.17.3",
        ports = Seq.empty,
        user = Some("nobody"),
        entrypoint = Seq(
          "sh",
          "-c",
          Seq(
            s"""test "$$(id -u)" != 0 || exit 10""",
            s"""test "$$(cat $copiedDirectory/a.txt)" = alpha || exit 11""",
            s"""test "$$(cat $copiedDirectory/nested/b.txt)" = beta || exit 12""",
            s"""test -x $copiedDirectory/run.sh || exit 13""",
            s"""test ! -x $copiedDirectory/a.txt || exit 14""",
            s"""test "$$(cat $copiedFile)" = single || exit 15""",
          ).mkString("; "),
        ),
        reuse = DockerReusePolicy.ReuseDisabled,
        autoRemove = false,
        healthCheck = ContainerHealthCheck.exitCodeCheck(),
      )
    }
  }

  object RunningContainer extends ContainerDef {
    override def config: Config = {
      Config(
        registry = Some("public.ecr.aws"),
        image = "docker/library/busybox:1.36.1",
        ports = Seq.empty,
        entrypoint = Seq("sleep", keepRunningSeconds),
        reuse = DockerReusePolicy.ReuseEnabled,
        healthCheck = ContainerHealthCheck.succeed,
      )
    }
  }

  final class TestContainers[T](base: ContainerResource[Task, T], client: DockerClientWrapper[Task]) {
    val labels: Map[String, String] = Map(testLabel -> UUID.randomUUID().toString)

    def configured(configure: ContainerConfig[T] => ContainerConfig[T]): ContainerResource[Task, T] = {
      val config = configure(base.config)
      base.copy(config = config.copy(userTags = config.userTags ++ labels))
    }

    def removeAll: Task[Unit] = ZIO.attempt {
      client.rawClient.listContainersCmd().withShowAll(true).withLabelFilter(labels.asJava).exec().asScala.foreach {
        c => client.rawClient.removeContainerCmd(c.getId).withForce(true).exec()
      }
    }
  }

  def testContainers[T](base: ContainerResource[Task, T], client: DockerClientWrapper[Task]): ZIO[Scope, Nothing, TestContainers[T]] = {
    ZIO.acquireRelease(ZIO.succeed(new TestContainers(base, client)))(_.removeAll.orDie)
  }

  final class HostFiles(val root: Path) {
    val directory: Path = root.resolve("dir")
    val single: Path = root.resolve("single.txt")
    val first: Path = root.resolve("first.txt")
    val second: Path = root.resolve("second.txt")
    val missing: Path = root.resolve("missing")
  }

  def createHostFiles(): HostFiles = {
    val buildDirectory = Paths.get(classOf[HostFiles].getProtectionDomain.getCodeSource.getLocation.toURI).getParent
    val files = new HostFiles(Files.createTempDirectory(buildDirectory, "container-files"))
    Files.createDirectories(files.directory.resolve("nested"))
    write(files.directory.resolve("a.txt"), "alpha", "rw-------")
    write(files.directory.resolve("nested").resolve("b.txt"), "beta", "rw-------")
    write(files.directory.resolve("run.sh"), "#!/bin/sh\n", "rwx------")
    write(files.single, "single", "rw-------")
    write(files.first, "first", "rw-r--r--")
    write(files.second, "second", "rw-r--r--")
    files
  }

  private def write(path: Path, content: String, permissions: String): Unit = {
    Files.write(path, content.getBytes(StandardCharsets.UTF_8))
    Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(permissions))
    ()
  }
}

final class ContainerFilesTest extends Spec2[IO] {

  override protected def config: TestConfig = super.config.copy(
    moduleOverrides = new ModuleDef {
      make[ContainerResource[Task, VerifyingContainer.Tag]].from(VerifyingContainer.make[Task])
      make[ContainerResource[Task, RunningContainer.Tag]].from(RunningContainer.make[Task])
    }
  )

  private val hostFiles = ZIO.acquireRelease(ZIO.attempt(createHostFiles()))(files => ZIO.attempt(IzFiles.erase(files.root)).orDie)

  "Container files" should {

    "be copied before the container starts and be readable by a non-root user" in {
      (resource: ContainerResource[Task, VerifyingContainer.Tag]) =>
        ZIO.scoped {
          for {
            files <- hostFiles
            _ <- resource
              .copy(config =
                resource.config.copy(files =
                  Seq(
                    ContainerFile(files.directory, copiedDirectory),
                    ContainerFile(files.single, copiedFile),
                  )
                )
              )
              .use(_ => ZIO.unit)
          } yield ()
        }
    }

    "fail naming the host path when it is missing, with and without reuse" in {
      (fresh: ContainerResource[Task, VerifyingContainer.Tag], reused: ContainerResource[Task, RunningContainer.Tag], client: DockerClientWrapper[Task]) =>
        ZIO.scoped {
          for {
            files <- hostFiles
            containers <- testContainers(reused, client)
            freshResult <- fresh.copy(config = fresh.config.copy(files = Seq(ContainerFile(files.missing, copiedFile)))).use(_ => ZIO.unit).either
            reusedResult <- containers.configured(_.copy(files = Seq(ContainerFile(files.missing, reusedFile)))).use(_ => ZIO.unit).either
            _ <- ZIO.foreachDiscard(List(freshResult, reusedResult)) {
              result =>
                assert2[IO](result.swap.exists {
                  case failure: DockerFailureException => failure.getMessage.contains(files.missing.toString)
                  case _ => false
                })
            }
          } yield ()
        }
    }

    "fail on an empty host path or a relative container path" in {
      (resource: ContainerResource[Task, VerifyingContainer.Tag]) =>
        ZIO.scoped {
          for {
            files <- hostFiles
            invalid = List(
              ContainerFile(Paths.get(""), copiedFile) -> "the host path is empty",
              ContainerFile(files.single, "relative/single.txt") -> "the container path is not absolute",
            )
            _ <- ZIO.foreachDiscard(invalid) {
              case (file, reason) =>
                resource.copy(config = resource.config.copy(files = Seq(file))).use(_ => ZIO.unit).either.flatMap {
                  result =>
                    assert2[IO](result.swap.exists {
                      case failure: DockerFailureException =>
                        failure.getCause.isInstanceOf[IllegalArgumentException] &&
                        failure.getCause.getMessage.endsWith(reason) &&
                        failure.getMessage.contains(s"`${file.containerPath}`")
                      case _ => false
                    })
                }
            }
          } yield ()
        }
    }

    "reuse only containers created with identical files" in {
      (resource: ContainerResource[Task, RunningContainer.Tag], client: DockerClientWrapper[Task]) =>
        ZIO.scoped {
          for {
            files <- hostFiles
            containers <- testContainers(resource, client)
            withFile = (path: Path) => containers.configured(_.copy(files = Seq(ContainerFile(path, reusedFile))))
            ids <- withFile(files.first).use {
              first =>
                withFile(files.first).use {
                  sameFiles =>
                    withFile(files.second).use {
                      otherFiles =>
                        containers.configured(identity).use(noFiles => ZIO.succeed((first.id, sameFiles.id, otherFiles.id, noFiles.id)))
                    }
                }
            }
            (first, sameFiles, otherFiles, noFiles) = ids
            _ <- assert2[IO](first == sameFiles)
            _ <- assert2[IO](Set(first, otherFiles, noFiles).size == 3)
          } yield ()
        }
    }

  }

}
