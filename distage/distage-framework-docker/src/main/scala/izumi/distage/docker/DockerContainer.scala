package izumi.distage.docker

import izumi.distage.docker.Docker._
import izumi.distage.docker.healthcheck.ContainerHealthCheck.VerifiedContainerConnectivity
import izumi.distage.model.effect.{DIEffect, DIEffectAsync}
import izumi.distage.model.providers.Functoid
import izumi.fundamentals.platform.language.Quirks._
import izumi.logstage.api.IzLogger

final case class DockerContainer[Tag](
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
  def resource[F[_]](conf: ContainerDef): (DockerClientWrapper[F], IzLogger, DIEffect[F], DIEffectAsync[F]) => ContainerResource[F, conf.Tag] = {
    new ContainerResource[F, conf.Tag](conf.config, _, _)(_, _)
  }

  implicit final class DockerProviderExtensions[F[_], T](private val self: Functoid[ContainerResource[F, T]]) extends AnyVal {
    /**
      * Allows you to modify [[izumi.distage.docker.Docker.ContainerConfig]] while summoning additional dependencies from the object graph using [[Functoid]].
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
        case (that, f) =>
          import that._
          that.copy(config = f(that.config))
      }
    }

    def dependOnDocker(containerDecl: ContainerDef)(implicit tag: distage.Tag[DockerContainer[containerDecl.Tag]]): Functoid[ContainerResource[F, T]] = {
      self.addDependency[DockerContainer[containerDecl.Tag]]
    }

    def dependOnDocker[T2](implicit tag: distage.Tag[DockerContainer[T2]]): Functoid[ContainerResource[F, T]] = {
      self.addDependency[DockerContainer[T2]]
    }

    def connectToNetwork[T2](
      implicit tag1: distage.Tag[ContainerNetworkDef.ContainerNetwork[T2]],
      tag2: distage.Tag[ContainerResource[F, T]],
      tag3: distage.Tag[Docker.ContainerConfig[T]],
    ): Functoid[ContainerResource[F, T]] = {
      tag1.discard()
      tag3.discard()
      modifyConfig {
        net: ContainerNetworkDef.ContainerNetwork[T2] => old: Docker.ContainerConfig[T] =>
          old.copy(networks = old.networks + net)
      }
    }

    def connectToNetwork(
      networkDecl: ContainerNetworkDef
    )(implicit tag1: distage.Tag[ContainerNetworkDef.ContainerNetwork[networkDecl.Tag]],
      tag2: distage.Tag[ContainerResource[F, T]],
      tag3: distage.Tag[Docker.ContainerConfig[T]],
    ): Functoid[ContainerResource[F, T]] = {
      tag1.discard()
      tag3.discard()
      modifyConfig {
        net: ContainerNetworkDef.ContainerNetwork[networkDecl.Tag] => old: Docker.ContainerConfig[T] =>
          old.copy(networks = old.networks + net)
      }
    }
  }

}
