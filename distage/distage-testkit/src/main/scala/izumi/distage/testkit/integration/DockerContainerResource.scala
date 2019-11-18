package izumi.distage.testkit.integration

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.{Bind, ExposedPort, Volume}
import izumi.distage.model.definition.DIResource
import izumi.distage.model.monadic.DIEffect
import izumi.distage.testkit.integration.Docker.{ContainerId, DockerPort}
import izumi.functional.Value
import izumi.fundamentals.platform.network.IzSockets


case class DockerContainer[Tag](id: Docker.ContainerId, mapping: Map[Docker.DockerPort, Int])

trait ContainerDecl[T] {
  type Tag = T
  type Descriptor = ContainerDecl[T]
  type Type = DockerContainer[T]

  def config: Docker.ContainerConfig

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

  case class Mount(host: String, container: String)
  case class ContainerConfig(
                              name: String,
                              ports: Set[DockerPort],
                              env: Map[String, String] = Map.empty,
                              cmd: Seq[String] = Seq.empty,
                              entrypoint: Seq[String] = Seq.empty,
                              cwd: Option[String] = None,
                              user: Option[String] = None,
                              mounts: Seq[Mount] = Seq.empty,
                            )

}

object DockerContainerResource {
  def apply[F[_]] = new Aux[F]

  class Aux[F[_]] {
    def make[T](conf: ContainerDecl[T]): (DIEffect[F], DockerClient) => DIResource[F, DockerContainer[T]] =
      (F, c) => new DockerContainerResource[F, T](c)(F, conf)
  }


}

class DockerContainerResource[F[_] : DIEffect, T: ContainerDecl]
(
  client: DockerClient,
) extends DIResource[F, DockerContainer[T]] {
  private val config = implicitly[ContainerDecl[T]].config

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

        val baseCmd = client
          .createContainerCmd(config.name)
          .withLabels(Map("type" -> "distage-testkit").asJava)

        val volumes = config.mounts.map(m =>  new Bind(m.host, new Volume(m.container), true) )

        val res = Value(baseCmd)
          .mut(exposed.nonEmpty)(_.withExposedPorts(exposed: _*))
          .mut(specs.nonEmpty)(_.withPortSpecs(specs: _*))
          .mut(config.env.nonEmpty)(_.withEnv(config.env.map { case (k, v) => s"$k=$v" }.toList.asJava))
          .mut(config.cmd.nonEmpty)(_.withCmd(config.cmd.toList.asJava))
          .mut(config.entrypoint.nonEmpty)(_.withEntrypoint(config.cmd.toList.asJava))
          .mut(config.cwd)((cwd, cmd) => cmd.withWorkingDir(cwd))
          .mut(config.user)((user, cmd) => cmd.withUser(user))
          .mut(volumes.nonEmpty)(_.withVolumes(volumes.map(_.getVolume).asJava))
          .mut(volumes.nonEmpty)(_.withBinds(volumes.toList.asJava))
          .get
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





