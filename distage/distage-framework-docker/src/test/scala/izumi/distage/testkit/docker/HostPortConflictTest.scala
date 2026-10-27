package izumi.distage.testkit.docker

import distage.ModuleDef
import izumi.distage.docker.impl.{ContainerResource, DockerClientWrapper}
import izumi.distage.docker.model.Docker.{DockerPort, DockerReusePolicy}
import izumi.distage.testkit.docker.ContainerFilesTest.*
import izumi.distage.testkit.docker.HostPortConflictTest.*
import izumi.distage.testkit.model.TestConfig
import izumi.distage.testkit.scalatest.{AssertZIO, Spec2}
import izumi.fundamentals.platform.language.Quirks.*
import zio.{IO, Scope, Task, ZIO}

import java.net.{InetSocketAddress, ServerSocket}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}
import scala.jdk.CollectionConverters.*

object HostPortConflictTest {
  final val staticPort: DockerPort = DockerPort.TCP(8080)
  final val dynamicPort: DockerPort = DockerPort.DynamicTCP("conflict")

  def occupiedPort: ZIO[Scope, Throwable, Int] = {
    ZIO
      .acquireRelease(ZIO.attempt {
        val socket = new ServerSocket()
        socket.bind(new InetSocketAddress(0))
        socket
      })(socket => ZIO.succeed(socket.close()))
      .map(_.getLocalPort)
  }

  def choosingHostPorts[T](base: ContainerResource[Task, T])(choose: (() => Int) => Int): ContainerResource[Task, T] = {
    new ContainerResource[Task, T](base.config, base.client, base.logger, base.deps)(base.F, base.P, base.T) {
      override protected def freeHostPort(): Int = choose(() => super.freeHostPort())
    }
  }

  def mappedHostPorts(container: RunningContainer.Container, port: DockerPort): Set[Int] = {
    container.connectivity.dockerPorts(port).toList.map(_.port).toSet
  }
}

final class HostPortConflictTest extends Spec2[IO] with AssertZIO {

  override protected def config: TestConfig = super.config.copy(
    moduleOverrides = new ModuleDef {
      make[ContainerResource[Task, RunningContainer.Tag]].from(RunningContainer.make[Task])
    }
  )

  private def startsWhenFirstChosenHostPortIsTaken(
    resource: ContainerResource[Task, RunningContainer.Tag],
    client: DockerClientWrapper[Task],
    port: DockerPort,
  ): Task[Unit] = {
    ZIO.scoped {
      for {
        containers <- testContainers(resource, client)
        occupied <- occupiedPort
        firstChoice = new AtomicBoolean(true)
        conflicting = choosingHostPorts(containers.configured(_.copy(ports = Seq(port), reuse = DockerReusePolicy.ReuseDisabled, autoRemove = false))) {
          probe => if (firstChoice.getAndSet(false)) occupied else probe()
        }
        mapped <- conflicting.use(container => ZIO.succeed(mappedHostPorts(container, port)))
        _ <- assertIO(!firstChoice.get())
        _ <- assertIO(mapped.nonEmpty)
        _ <- assertIO(!mapped.contains(occupied))
      } yield ()
    }
  }

  "Container resource" should {

    "choose another host port for a static port when the chosen one is taken" in {
      (resource: ContainerResource[Task, RunningContainer.Tag], client: DockerClientWrapper[Task]) =>
        startsWhenFirstChosenHostPortIsTaken(resource, client, staticPort)
    }

    "choose another host port for a dynamic port when the chosen one is taken" in {
      (resource: ContainerResource[Task, RunningContainer.Tag], client: DockerClientWrapper[Task]) =>
        startsWhenFirstChosenHostPortIsTaken(resource, client, dynamicPort)
    }

    "give up after a bounded number of attempts when every chosen host port is taken" in {
      (resource: ContainerResource[Task, RunningContainer.Tag], client: DockerClientWrapper[Task]) =>
        ZIO.scoped {
          for {
            containers <- testContainers(resource, client)
            occupied <- occupiedPort
            choices = new AtomicInteger(0)
            conflicting = choosingHostPorts(containers.configured(_.copy(ports = Seq(staticPort), reuse = DockerReusePolicy.ReuseDisabled, autoRemove = false))) {
              _ =>
                choices.incrementAndGet().discard()
                occupied
            }
            failedWithConflict <- conflicting.use(_ => ZIO.succeed(false)).catchSome {
              case failure if ContainerResource.isHostPortConflict(failure) => ZIO.succeed(true)
            }
            _ <- assertIO(failedWithConflict)
            _ <- assertIO(choices.get() == ContainerResource.hostPortConflictMaxAttempts)
            remaining <- ZIO.attempt(client.rawClient.listContainersCmd().withShowAll(true).withLabelFilter(containers.labels.asJava).exec().size())
            _ <- assertIO(remaining == 0)
          } yield ()
        }
    }

  }

}
