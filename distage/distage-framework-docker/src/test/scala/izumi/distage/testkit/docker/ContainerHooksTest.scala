package izumi.distage.testkit.docker

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.exception.NotFoundException
import distage.ModuleDef
import izumi.distage.docker.impl.{ContainerResource, DockerClientWrapper}
import izumi.distage.docker.model.Docker.{ContainerFile, ContainerId, DockerReusePolicy}
import izumi.distage.docker.model.{ContainerHook, DockerFailureException}
import izumi.distage.testkit.docker.ContainerFilesTest.*
import izumi.distage.testkit.model.TestConfig
import izumi.distage.testkit.scalatest.{AssertZIO, Spec2}
import zio.{IO, Task, ZIO}

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicReference
import scala.util.{Try, Using}

final class ContainerHooksTest extends Spec2[IO] with AssertZIO {

  override protected def config: TestConfig = super.config.copy(
    moduleOverrides = new ModuleDef {
      make[ContainerResource[Task, RunningContainer.Tag]].from(RunningContainer.make[Task])
    }
  )

  private final class RecordingHook(val reuseKey: String, failure: Option[Throwable]) extends ContainerHook {
    val observed = new AtomicReference[Option[(ContainerId, String)]](None)

    override def afterCreate(client: DockerClient, containerId: ContainerId): Unit = {
      observed.set(Some((containerId, client.inspectContainerCmd(containerId.name).exec().getState.getStatus)))
      failure.foreach(throw _)
    }
  }

  "After-create hooks" should {

    "run on the created container before it is started" in {
      (resource: ContainerResource[Task, RunningContainer.Tag], client: DockerClientWrapper[Task]) =>
        val hook = new RecordingHook("observe", None)
        ZIO.scoped {
          for {
            containers <- testContainers(resource, client)
            containerId <- containers.configured(_.copy(afterCreate = Seq(hook))).use(c => ZIO.succeed(c.id))
            _ <- assertIO(hook.observed.get().contains((containerId, "created")))
          } yield ()
        }
    }

    "run after `files` are copied" in {
      (resource: ContainerResource[Task, RunningContainer.Tag], client: DockerClientWrapper[Task]) =>
        val copiedPath = "/opt/distage/hooked.txt"
        val sawCopiedFile = new AtomicReference[Option[Boolean]](None)
        val hook = new ContainerHook {
          override def reuseKey: String = "check-copied-file"
          override def afterCreate(client: DockerClient, containerId: ContainerId): Unit = {
            val found = Try(Using.resource(client.copyArchiveFromContainerCmd(containerId.name, copiedPath).exec())(_.read())).isSuccess
            sawCopiedFile.set(Some(found))
          }
        }
        ZIO.scoped {
          for {
            containers <- testContainers(resource, client)
            hostFile <- ZIO.acquireRelease(ZIO.attempt(Files.createTempFile("container-hooks", ".txt")))(p => ZIO.attempt(Files.deleteIfExists(p)).orDie)
            _ <- ZIO.attempt(Files.write(hostFile, "copied".getBytes(StandardCharsets.UTF_8)))
            _ <- containers.configured(_.copy(files = Seq(ContainerFile(hostFile, copiedPath)), afterCreate = Seq(hook))).use(_ => ZIO.unit)
            _ <- assertIO(sawCopiedFile.get().contains(true))
          } yield ()
        }
    }

    "remove the container and keep the hook's exception as the cause when a hook fails" in {
      (resource: ContainerResource[Task, RunningContainer.Tag], client: DockerClientWrapper[Task]) =>
        val cause = new IllegalStateException("hook failed")
        val hook = new RecordingHook("failing", Some(cause))
        ZIO
          .scoped {
            for {
              containers <- testContainers(resource, client)
              result <- containers.configured(_.copy(afterCreate = Seq(hook))).use(_ => ZIO.unit).either
              _ <- assertIO(result match {
                case Left(failure: DockerFailureException) => failure.getCause eq cause
                case _ => false
              })
              containerId <- ZIO.fromOption(hook.observed.get().map(_._1)).orElseFail(new IllegalStateException("the hook did not run"))
              removed <- ZIO.attempt(client.rawClient.inspectContainerCmd(containerId.name).exec()).either
              _ <- assertIO(removed match {
                case Left(_: NotFoundException) => true
                case _ => false
              })
            } yield ()
          }
    }

    "remove the container when it fails to start" in {
      (resource: ContainerResource[Task, RunningContainer.Tag], client: DockerClientWrapper[Task]) =>
        val hook = new RecordingHook("unstartable", None)
        ZIO
          .scoped {
            for {
              containers <- testContainers(resource, client)
              unstartable = containers.configured(
                _.copy(
                  entrypoint = Seq("/nonexistent-entrypoint"),
                  reuse = DockerReusePolicy.ReuseDisabled,
                  autoRemove = false,
                  afterCreate = Seq(hook),
                )
              )
              result <- unstartable.use(_ => ZIO.unit).either
              _ <- assertIO(result.isLeft)
              containerId <- ZIO.fromOption(hook.observed.get().map(_._1)).orElseFail(new IllegalStateException("the hook did not run"))
              removed <- ZIO.attempt(client.rawClient.inspectContainerCmd(containerId.name).exec()).either
              _ <- assertIO(removed match {
                case Left(_: NotFoundException) => true
                case _ => false
              })
            } yield ()
          }
    }

    "reuse only containers created with hooks of identical reuse keys" in {
      (resource: ContainerResource[Task, RunningContainer.Tag], client: DockerClientWrapper[Task]) =>
        ZIO
          .scoped {
            for {
              containers <- testContainers(resource, client)
              withHook = (key: String) => containers.configured(_.copy(afterCreate = Seq(new RecordingHook(key, None))))
              ids <- withHook("a").use {
                first =>
                  withHook("a").use {
                    sameKey =>
                      withHook("b").use(otherKey => ZIO.succeed((first.id, sameKey.id, otherKey.id)))
                  }
              }
              (first, sameKey, otherKey) = ids
              _ <- assertIO(first == sameKey)
              _ <- assertIO(first != otherKey)
            } yield ()
          }
    }

  }

}
