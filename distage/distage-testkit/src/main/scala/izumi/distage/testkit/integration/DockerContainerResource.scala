package izumi.distage.testkit.integration

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.DockerCmdExecFactory
import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.core.{DefaultDockerClientConfig, DockerClientBuilder}
import com.github.dockerjava.netty.NettyDockerCmdExecFactory
import distage.TagK
import izumi.distage.config.annotations.ConfPath
import izumi.distage.model.definition.{DIResource, ModuleDef}
import izumi.distage.model.monadic.DIEffect
import izumi.distage.roles.model.IntegrationCheck
import izumi.distage.testkit.integration.Docker.{ContainerId, DockerPort}
import izumi.fundamentals.platform.integration.ResourceCheck
import izumi.fundamentals.platform.network.IzSockets

trait DockerContainer {
  def id: Docker.ContainerId
  def mapping: Map[Docker.DockerPort, Int]
}

trait DockerContainerF[D] {
  def create(id: ContainerId, mapping: Map[Docker.DockerPort, Int]): D
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

  case class ContainerConfig(name: String, ports: Set[DockerPort])

}

class DockerClientWrapper(val client: DockerClient) extends IntegrationCheck {
  override def resourcesAvailable(): ResourceCheck = {
    try {
      client.infoCmd().exec()
      ResourceCheck.Success()
    } catch {
      case t: Throwable =>
        ResourceCheck.ResourceUnavailable("Docker daemon is unavailable", Some(t))
    }

  }
}

class DockerContainerResource[F[_] : DIEffect, D <: DockerContainer : DockerContainerF]
(
  client: DockerClient,
  config: Docker.ContainerConfig,
) extends DIResource[F, D] {
  override def acquire: F[D] = {
    DIEffect[F]
      .maybeSuspend {
        val ports = config.ports.map {
          p =>
            p -> IzSockets.temporaryLocalPort()
        }

        val specs = ports.map {
          case (container, local) =>
            s"${container.number}:${local}"
        }.toSeq


        val exposed = config.ports.toSeq.map {
          case DockerPort.TCP(number) =>
            ExposedPort.tcp(number)
        }
        import scala.jdk.CollectionConverters._

        val res = client
          .createContainerCmd(config.name)
          .withExposedPorts(exposed :_*)
          .withPortSpecs(specs: _*)
          .withLabels(Map("type" -> "distage-testkit").asJava)
          .exec()

        client.startContainerCmd(res.getId).exec()

        implicitly[DockerContainerF[D]].create(ContainerId(res.getId), ports.toMap)
      }
  }

  override def release(resource: D): F[Unit] = {
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

class DockerExecFactoryResource[F[_] : DIEffect](config: Docker.ClientConfig@ConfPath("docker")) extends DIResource[F, DockerCmdExecFactory] {
  override def acquire: F[DockerCmdExecFactory] = {
    DIEffect[F].maybeSuspend {
      new NettyDockerCmdExecFactory()
        .withReadTimeout(config.readTimeoutMs)
        .withConnectTimeout(config.connectTimeoutMs)
    }
  }

  override def release(resource: DockerCmdExecFactory): F[Unit] = DIEffect[F].maybeSuspend(resource.close())
}

class DockerClientResource[F[_] : DIEffect](config: Docker.ClientConfig@ConfPath("docker"), factory: DockerCmdExecFactory) extends DIResource[F, DockerClientWrapper] {
  override def acquire: F[DockerClientWrapper] = {

    val dcc = DefaultDockerClientConfig.createDefaultConfigBuilder().build()
    //        .withDockerHost("tcp://docker.somewhere.tld:2376")
    //        .withDockerTlsVerify(true)
    //        .withDockerCertPath("/home/user/.docker")
    //        .withRegistryUsername(registryUser)
    //        .withRegistryPassword(registryPass)
    //        .withRegistryEmail(registryMail)
    //        .withRegistryUrl(registryUrl)
    //        .build


    val client = DockerClientBuilder
      .getInstance(dcc)
      .withDockerCmdExecFactory(factory)
      .build

    DIEffect[F].maybeSuspend(new DockerClientWrapper(client))
  }

  override def release(resource: DockerClientWrapper): F[Unit] = DIEffect[F].maybeSuspend(resource.client.close())
}

class DockerContainerModule[F[_] : DIEffect : TagK] extends ModuleDef {
  make[DockerCmdExecFactory].fromResource[DockerExecFactoryResource[F]]
  make[DockerClientWrapper].fromResource[DockerClientResource[F]]
  make[DockerClient].from {
    wrapper: DockerClientWrapper => wrapper.client
  }
  many[IntegrationCheck].ref[DockerClientWrapper]
}