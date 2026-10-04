package izumi.distage.testkit.docker

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.exception.NotFoundException
import distage.ModuleDef
import izumi.distage.docker.impl.{ContainerResource, DockerClientWrapper}
import izumi.distage.docker.model.{ContainerHook, DockerFailureException}
import izumi.distage.testkit.docker.ContainerFilesTest.*
import izumi.distage.testkit.model.TestConfig
import izumi.distage.testkit.scalatest.{AssertZIO, Spec2}
import zio.{IO, Task, ZIO}

import java.util.concurrent.atomic.AtomicReference
import scala.jdk.CollectionConverters.*

final class ContainerHooksTest extends Spec2[IO] with AssertZIO {

  override protected def config: TestConfig = super.config.copy(
    moduleOverrides = new ModuleDef {
      make[ContainerResource[Task, RunningContainer.Tag]].from(RunningContainer.make[Task])
    }
  )

  private final class RecordingHook(val reuseKey: String, failure: Option[Throwable]) extends ContainerHook {
    val observed = new AtomicReference[Option[(String, String)]](None)

    override def afterCreate(client: DockerClient, containerId: String): Unit = {
      observed.set(Some((containerId, client.inspectContainerCmd(containerId).exec().getState.getStatus)))
      failure.foreach(throw _)
    }
  }

  private def removeTestContainers(client: DockerClientWrapper[Task]): Task[Unit] = ZIO.attempt {
    client.rawClient.listContainersCmd().withShowAll(true).withLabelFilter(testLabels.asJava).exec().asScala.foreach {
      c => client.rawClient.removeContainerCmd(c.getId).withForce(true).exec()
    }
  }

  "After-create hooks" should {

    "run on the created container before it is started" in {
      (resource: ContainerResource[Task, RunningContainer.Tag], client: DockerClientWrapper[Task]) =>
        val hook = new RecordingHook("observe", None)
        ZIO
          .scoped {
            for {
              _ <- removeTestContainers(client)
              containerId <- resource.copy(config = resource.config.copy(afterCreate = Seq(hook))).use(c => ZIO.succeed(c.id.name))
              _ <- assertIO(hook.observed.get().contains((containerId, "created")))
            } yield ()
          }.ensuring(removeTestContainers(client).orDie)
    }

    "remove the container and keep the hook's exception as the cause when a hook fails" in {
      (resource: ContainerResource[Task, RunningContainer.Tag], client: DockerClientWrapper[Task]) =>
        val cause = new IllegalStateException("hook failed")
        val hook = new RecordingHook("failing", Some(cause))
        ZIO
          .scoped {
            for {
              _ <- removeTestContainers(client)
              result <- resource.copy(config = resource.config.copy(afterCreate = Seq(hook))).use(_ => ZIO.unit).either
              _ <- assertIO(result match {
                case Left(failure: DockerFailureException) => failure.getCause eq cause
                case _ => false
              })
              containerId <- ZIO.fromOption(hook.observed.get().map(_._1)).orElseFail(new IllegalStateException("the hook did not run"))
              removed <- ZIO.attempt(client.rawClient.inspectContainerCmd(containerId).exec()).either
              _ <- assertIO(removed match {
                case Left(_: NotFoundException) => true
                case _ => false
              })
            } yield ()
          }.ensuring(removeTestContainers(client).orDie)
    }

    "reuse only containers created with hooks of identical reuse keys" in {
      (resource: ContainerResource[Task, RunningContainer.Tag], client: DockerClientWrapper[Task]) =>
        def withHook(key: String): ContainerResource[Task, RunningContainer.Tag] = {
          resource.copy(config = resource.config.copy(afterCreate = Seq(new RecordingHook(key, None))))
        }
        ZIO
          .scoped {
            for {
              _ <- removeTestContainers(client)
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
          }.ensuring(removeTestContainers(client).orDie)
    }

  }

}
