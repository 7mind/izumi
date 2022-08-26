package izumi.distage.docker

import izumi.distage.docker.ContainerNetworkDef.ContainerNetwork
import izumi.distage.docker.Docker._
import izumi.distage.docker.healthcheck.ContainerHealthCheck.VerifiedContainerConnectivity
import izumi.distage.model.effect.{QuasiAsync, QuasiIO}
import izumi.distage.model.providers.Functoid
import izumi.fundamentals.platform.language.Quirks._
import izumi.logstage.api.IzLogger

final case class DockerContainer[+Tag](
  id: ContainerId,
  name: String,
  hostName: String,
  labels: Map[String, String],
  containerConfig: ContainerConfig[Tag],
  clientConfig: ClientConfig,
  connectivity: ReportedContainerConnectivity,
  availablePorts: VerifiedContainerConnectivity,
) {
  override def toString: String = {
    val out = new StringBuilder()
    out.append(s"$name/${containerConfig.image}@${connectivity.dockerHost.getOrElse("localhost")}")
    availablePorts match {
      case VerifiedContainerConnectivity.HasAvailablePorts(availablePorts) =>
        out.append(" {")
        out.append(availablePorts.toString)
        out.append('}')
      case VerifiedContainerConnectivity.NoAvailablePorts() =>
    }

    out.toString()
  }
}

object DockerContainer {
  def resource[F[_]](conf: ContainerDef): (DockerClientWrapper[F], IzLogger, QuasiIO[F], QuasiAsync[F]) => ContainerResource[F, conf.Tag] = {
    new ContainerResource[F, conf.Tag](conf.config, _, _)(_, _)
  }

  implicit final class DockerProviderExtensions[F[_], T](private val self: Functoid[ContainerResource[F, T]]) extends AnyVal {
    /**
      * Allows you to modify [[izumi.distage.docker.Docker.ContainerConfig]] while summoning additional dependencies from the object graph using [[izumi.distage.model.providers.Functoid]].
      *
      * Example:
      *
      * {{{
      *   KafkaDocker
      *     .make[F]
      *     .modifyConfig {
      *       (zookeeperDocker: ZookeeperDocker.Container, net: KafkaZookeeperNetwork.Network) =>
      *         (old: KafkaDocker.Config) =>
      *           val zkEnv = KafkaDocker.config.env ++ Map("KAFKA_ZOOKEEPER_CONNECT" -> s"${zookeeperDocker.hostName}:2181")
      *           val zkNet = KafkaDocker.config.networks + net
      *           old.copy(env = zkEnv, networks = zkNet)
      *     }
      * }}}
      */
    def modifyConfig(
      modify: Functoid[Docker.ContainerConfig[T] => Docker.ContainerConfig[T]]
    )(implicit tag: distage.Tag[ContainerResource[F, T]]
    ): Functoid[ContainerResource[F, T]] = {
      self.zip(modify).map {
        case (self, f) =>
          import self.{F, P}
          self.copy(config = f(self.config))
      }
    }

    def modifyConfig(
      modify: Docker.ContainerConfig[T] => Docker.ContainerConfig[T]
    ): Functoid[ContainerResource[F, T]] = {
      self.mapSame {
        self =>
          import self.{F, P}
          self.copy(config = modify(self.config))
      }
    }

    def dependOnContainer(containerDecl: ContainerDef)(implicit tag: distage.Tag[DockerContainer[containerDecl.Tag]]): Functoid[ContainerResource[F, T]] = {
      self.addDependency[DockerContainer[containerDecl.Tag]]
    }

    def dependOnContainer[T2](implicit tag: distage.Tag[DockerContainer[T2]]): Functoid[ContainerResource[F, T]] = {
      self.addDependency[DockerContainer[T2]]
    }

    /**
      * Export as environment variables inside the container the randomized values of ports of the argument running docker container
      *
      * @example
      * {{{
      * class KafkaDockerModule[F[_]: TagK] extends ModuleDef {
      *   make[KafkaDocker.Container].fromResource {
      *     KafkaDocker
      *       .make[F]
      *       .connectToNetwork(KafkaZookeeperNetwork)
      *       .dependOnContainerPorts(ZookeeperDocker)(2181 -> "KAFKA_ZOOKEEPER_CONNECT")
      *   }
      * }
      * }}}
      */
    def dependOnContainerPorts(
      containerDecl: ContainerDef
    )(ports: (Int, String)*
    )(implicit tag1: distage.Tag[DockerContainer[containerDecl.Tag]],
      tag2: distage.Tag[ContainerResource[F, T]],
      tag3: distage.Tag[Docker.ContainerConfig[T]],
    ): Functoid[ContainerResource[F, T]] = {
      containerDecl.discard()
      dependOnContainerPorts[containerDecl.Tag](ports: _*)
    }

    def dependOnContainerPorts[T2](
      ports: (Int, String)*
    )(implicit tag1: distage.Tag[DockerContainer[T2]],
      tag2: distage.Tag[ContainerResource[F, T]],
      tag3: distage.Tag[Docker.ContainerConfig[T]],
    ): Functoid[ContainerResource[F, T]] = {
      discard(tag1, tag3)
      modifyConfig {
        (original: DockerContainer[T2]) => (old: Docker.ContainerConfig[T]) =>
          val mapping = ports.map {
            case (port, envvar) =>
              (envvar, s"${original.hostName}:$port")
          }
          val newEnv = old.env ++ mapping
          old.copy(env = newEnv)
      }
    }

    def connectToNetwork(
      networkDecl: ContainerNetworkDef
    )(implicit tag1: distage.Tag[ContainerNetwork[networkDecl.Tag]],
      tag2: distage.Tag[ContainerResource[F, T]],
      tag3: distage.Tag[Docker.ContainerConfig[T]],
    ): Functoid[ContainerResource[F, T]] = {
      networkDecl.discard()
      connectToNetwork[networkDecl.Tag]
    }

    def connectToNetwork[T2](
      implicit tag1: distage.Tag[ContainerNetwork[T2]],
      tag2: distage.Tag[ContainerResource[F, T]],
      tag3: distage.Tag[Docker.ContainerConfig[T]],
    ): Functoid[ContainerResource[F, T]] = {
      discard(tag1, tag3)
      modifyConfig {
        (net: ContainerNetwork[T2]) => (old: Docker.ContainerConfig[T]) =>
          old.copy(networks = old.networks + net)
      }
    }
  }

}
