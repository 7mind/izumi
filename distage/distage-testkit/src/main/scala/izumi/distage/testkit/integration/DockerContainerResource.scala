package izumi.distage.testkit.integration

import java.util.concurrent.TimeUnit

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.{Bind, ExposedPort, PortBinding, Ports, Volume}
import izumi.distage.model.definition.DIResource
import izumi.distage.model.monadic.DIEffect.syntax._
import izumi.distage.model.monadic.{DIEffect, DIEffectAsync}
import izumi.distage.testkit.integration.Docker.{ContainerConfig, ContainerId, DockerPort, HealthCheckResult}
import izumi.functional.Value
import izumi.fundamentals.platform.integration.{PortCheck, ResourceCheck}
import izumi.fundamentals.platform.network.IzSockets

import scala.concurrent.duration.FiniteDuration

case class DockerContainer[Tag](id: Docker.ContainerId, mapping: Map[Docker.DockerPort, Int])

trait ContainerDecl[T] {
  type Tag = T
  type Descriptor = ContainerDecl[T]
  type Type = DockerContainer[T]
  type Config = ContainerConfig[T]

  def config: Config

  final implicit val self: Descriptor = this
}


object Docker {

  case class ContainerId(name: String) extends AnyVal

  sealed trait DockerPort {
    def number: Int
  }

  object DockerPort {

    case class TCP(number: Int) extends DockerPort

  }

  case class ClientConfig(readTimeoutMs: Int, connectTimeoutMs: Int)

  sealed trait HealthCheckResult

  object HealthCheckResult {

    case object Running extends HealthCheckResult

    case object Uknnown extends HealthCheckResult

    case class Failed(t: Throwable) extends HealthCheckResult

  }

  trait ContainerHealthCheck[Tag] {
    def check(container: DockerContainer[Tag]): HealthCheckResult
  }

  case class HealthCheckConfig()

  object ContainerHealthCheck {
    final def noCheck[T]: ContainerHealthCheck[T] = new ContainerHealthCheck[T] {
      override def check(container: DockerContainer[T]): HealthCheckResult = HealthCheckResult.Running
    }

    final def portCheck[T](exposedPort: DockerPort, timeout: FiniteDuration = FiniteDuration(1, TimeUnit.SECONDS)): ContainerHealthCheck[T] = new ContainerHealthCheck[T] {
      override def check(container: DockerContainer[T]): HealthCheckResult = {
        container.mapping.get(exposedPort) match {
          case Some(value) =>
            new PortCheck(timeout.toMillis.intValue()).checkPort("localhost", value, s"open port ${exposedPort} on ${container.id}") match {
              case _: ResourceCheck.Success =>
                HealthCheckResult.Running
              case f: ResourceCheck.Failure =>
                println(f)
                HealthCheckResult.Uknnown
            }
          case None =>
            HealthCheckResult.Failed(new RuntimeException(s"Port ${exposedPort} is not mapped!"))
        }

      }
    }
  }

  case class Mount(host: String, container: String)

  case class ContainerConfig[T](
                                 name: String,
                                 ports: Set[DockerPort],
                                 env: Map[String, String] = Map.empty,
                                 cmd: Seq[String] = Seq.empty,
                                 entrypoint: Seq[String] = Seq.empty,
                                 cwd: Option[String] = None,
                                 user: Option[String] = None,
                                 mounts: Seq[Mount] = Seq.empty,
                                 healthCheckInterval: FiniteDuration = FiniteDuration(1, TimeUnit.SECONDS),
                                 healthCheck: ContainerHealthCheck[T] = ContainerHealthCheck.noCheck[T],
                               )

}

object DockerContainerResource {
  def apply[F[_]] = new Aux[F]

  class Aux[F[_]] {
    def make[T](conf: ContainerDecl[T]): (DIEffect[F], DIEffectAsync[F], DockerClient) => DIResource[F, DockerContainer[T]] =
      (dieffect, dieffectasync, c) => new DockerContainerResource[F, T](c)(dieffect, dieffectasync, conf)
  }


}

class DockerContainerResource[F[_] : DIEffect : DIEffectAsync, T: ContainerDecl]
(
  client: DockerClient,
) extends DIResource[F, DockerContainer[T]] {
  private val config = implicitly[ContainerDecl[T]].config

  override def acquire: F[DockerContainer[T]] = {
    val ports = config.ports.map {
      p =>
        p -> IzSockets.temporaryLocalPort()
    }

    val specs = ports.map {
      case (container, local) =>

        val p = container match {
          case DockerPort.TCP(number) =>
            ExposedPort.tcp(number)
        }
        new PortBinding(Ports.Binding.bindPort(local), p)
    }.toSeq


    val exposed = config.ports.toSeq.map {
      case DockerPort.TCP(number) =>
        ExposedPort.tcp(number)
    }
    import scala.jdk.CollectionConverters._

    val baseCmd = client
      .createContainerCmd(config.name)
      .withLabels(Map("type" -> "distage-testkit").asJava)

    val volumes = config.mounts.map(m => new Bind(m.host, new Volume(m.container), true))

    def await(c: DockerContainer[T]): F[Unit] = {
      DIEffect[F]
        .maybeSuspend {
          try {
            val status = client.inspectContainerCmd(c.id.name).exec()
            println(status)
            if (status.getState.getRunning) {
              config.healthCheck.check(c)
            } else {
              HealthCheckResult.Failed(new RuntimeException(s"Container exited: ${c.id}, full status: $status"))
            }
          } catch {
            case t: Throwable =>
              HealthCheckResult.Failed(t)
          }
        }
        .flatMap {
          case HealthCheckResult.Running =>
            DIEffect[F].unit
          case HealthCheckResult.Failed(t) =>
            DIEffect[F].fail(new RuntimeException(s"Container failed: ${c.id}", t))
          case HealthCheckResult.Uknnown =>
            DIEffectAsync[F].sleep(config.healthCheckInterval).flatMap(_ => await(c))
        }
    }

    for {
      out <- DIEffect[F].maybeSuspend {
        val cmd = Value(baseCmd)
          .mut(exposed.nonEmpty)(_.withExposedPorts(exposed: _*))
          .mut(specs.nonEmpty)(_.withPortBindings(specs: _*))
          .mut(config.env.nonEmpty)(_.withEnv(config.env.map { case (k, v) => s"$k=$v" }.toList.asJava))
          .mut(config.cmd.nonEmpty)(_.withCmd(config.cmd.toList.asJava))
          .mut(config.entrypoint.nonEmpty)(_.withEntrypoint(config.cmd.toList.asJava))
          .mut(config.cwd)((cwd, cmd) => cmd.withWorkingDir(cwd))
          .mut(config.user)((user, cmd) => cmd.withUser(user))
          .mut(volumes.nonEmpty)(_.withVolumes(volumes.map(_.getVolume).asJava))
          .mut(volumes.nonEmpty)(_.withBinds(volumes.toList.asJava))
          .get

        val res = cmd.exec()

        client.startContainerCmd(res.getId).exec()

        DockerContainer[T](ContainerId(res.getId), ports.toMap)
      }
      _ <- await(out)
    } yield {
      out
    }

  }

  override def release(resource: DockerContainer[T]): F[Unit] = {
    DIEffect[F].maybeSuspend {
      try {
        client
          .stopContainerCmd(resource.id.name)
          .exec()
        ()
      } finally {
        client
          .removeContainerCmd(resource.id.name)
          .withForce(true)
          .exec()
        ()
      }
    }
  }


}





