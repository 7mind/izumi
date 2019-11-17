package izumi.distage.testkit.integration

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.ExposedPort
import izumi.distage.model.definition.DIResource
import izumi.distage.model.monadic.DIEffect
import izumi.distage.testkit.integration.Docker.{ContainerId, DockerPort}
import izumi.fundamentals.platform.network.IzSockets


case class DockerContainer[Tag](id: Docker.ContainerId, mapping: Map[Docker.DockerPort, Int])


object Docker {

  case class ContainerId(name: String) extends AnyVal

  sealed trait DockerPort {
    def number: Int
  }

  object DockerPort {

    case class TCP(number: Int) extends DockerPort

  }

  case class ClientConfig(readTimeoutMs: Int, connectTimeoutMs: Int)

  case class ContainerConfig(name: String, ports: Set[DockerPort])

}

trait ContainerTag

class DockerContainerResource[F[_] : DIEffect, T <: ContainerTag]
(
  client: DockerClient,
  config: Docker.ContainerConfig,
) extends DIResource[F, DockerContainer[T]] {
  override def acquire: F[DockerContainer[T]] = {
    DIEffect[F]
      .maybeSuspend {
        val ports = config.ports.map {
          p =>
            p -> IzSockets.temporaryLocalPort()
        }

        val specs = ports.map {
          case (container, local) =>
            s"${container.number}:$local"
        }.toSeq


        val exposed = config.ports.toSeq.map {
          case DockerPort.TCP(number) =>
            ExposedPort.tcp(number)
        }
        import scala.jdk.CollectionConverters._

        val res = client
          .createContainerCmd(config.name)
          .withExposedPorts(exposed: _*)
          .withPortSpecs(specs: _*)
          .withLabels(Map("type" -> "distage-testkit").asJava)
          .exec()

        client.startContainerCmd(res.getId).exec()

        DockerContainer[T](ContainerId(res.getId), ports.toMap)
      }
  }

  override def release(resource: DockerContainer[T]): F[Unit] = {
    DIEffect[F].maybeSuspend {
      try {
        client
          .stopContainerCmd(resource.id.name)
          .exec()
      } finally {
        client
          .removeContainerCmd(resource.id.name)
          .withForce(true)
          .exec()
      }
    }
  }


}





