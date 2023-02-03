package izumi.distage.docker

import distage.Tag
import izumi.distage.docker.ContainerNetworkDef.ContainerNetwork
import izumi.distage.docker.healthcheck.ContainerHealthCheck.VerifiedContainerConnectivity
import izumi.distage.docker.impl.{ContainerResource, DockerClientWrapper}
import izumi.distage.docker.model.Docker.*
import izumi.distage.model.definition.dsl.ModuleDefDSL
import izumi.distage.model.providers.Functoid
import izumi.distage.model.reflection.{IdContract, SafeType}
import izumi.functional.quasi.{QuasiAsync, QuasiIO}
import izumi.fundamentals.platform.language.Quirks.*
import izumi.logstage.api.IzLogger

final case class DockerContainer[+T](
  id: ContainerId,
  name: String,
  hostName: String,
  labels: Map[String, String],
  containerConfig: ContainerConfig[T],
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
  case class DependencyTag(tpe: SafeType)

  object DependencyTag {
    def get[T](implicit tag: Tag[DockerContainer[T]]): DependencyTag = DependencyTag(SafeType.get[DockerContainer[T]])

    implicit def tagIdContract: IdContract[DependencyTag] = new IdContract[DependencyTag] {
      override def repr(v: DependencyTag): String = s"container:${v.tpe}"
    }
  }
  def resource[F[_]](conf: ContainerDef): (DockerClientWrapper[F], IzLogger, Set[DockerContainer[Any]], QuasiIO[F], QuasiAsync[F]) => ContainerResource[F, conf.Tag] = {
    new ContainerResource[F, conf.Tag](conf.config, _, _, _)(_, _)
  }

  implicit final class DockerProviderExtensions[F[_], T](private val self: Functoid[ContainerResource[F, T]]) extends AnyVal {
    /**
      * Allows you to modify [[Docker.ContainerConfig]] while summoning additional dependencies from the object graph using [[izumi.distage.model.providers.Functoid]].
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

    def dependOnContainer(
      containerDecl: ContainerDef
    )(implicit tag: distage.Tag[DockerContainer[containerDecl.Tag]],
      selfTag: distage.Tag[DockerContainer[T]],
      mutateModule: ModuleDefDSL#MutationContext,
    ): Functoid[ContainerResource[F, T]] = {
      addContainerDependency[containerDecl.Tag]
      self
        .addDependency[DockerContainer[containerDecl.Tag]]
        .annotateParameter[Set[DockerContainer[Any]]](DependencyTag.get[containerDecl.Tag])
    }

    def dependOnContainer[T2](
      implicit tag: distage.Tag[DockerContainer[T2]],
      selfTag: distage.Tag[DockerContainer[T]],
      mutateModule: ModuleDefDSL#MutationContext,
    ): Functoid[ContainerResource[F, T]] = {
      addContainerDependency[T2]
      self
        .addDependency[DockerContainer[T2]]
        .annotateParameter[Set[DockerContainer[Any]]](DependencyTag.get[T2])
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
      selfTag: distage.Tag[DockerContainer[T]],
      mutateModule: ModuleDefDSL#MutationContext,
    ): Functoid[ContainerResource[F, T]] = {
      containerDecl.discard()
      dependOnContainerPorts[containerDecl.Tag](ports: _*)
    }

    def dependOnContainerPorts[T2](
      ports: (Int, String)*
    )(implicit tag1: distage.Tag[DockerContainer[T2]],
      tag2: distage.Tag[ContainerResource[F, T]],
      tag3: distage.Tag[Docker.ContainerConfig[T]],
      selfTag: distage.Tag[DockerContainer[T]],
      mutateModule: ModuleDefDSL#MutationContext,
    ): Functoid[ContainerResource[F, T]] = {
      discard(tag1, tag3)

      dependOnContainer[T2]
        .modifyConfig {
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

    private[this] def addContainerDependency[T2](
      implicit tag: distage.Tag[DockerContainer[T2]],
      selfTag: distage.Tag[DockerContainer[T]],
      mutateModule: ModuleDefDSL#MutationContext,
    ): Unit = {
      new mutateModule.dsl {
        many[DockerContainer[Any]]
          .named(DependencyTag.get[T])
          .ref[DockerContainer[T2]]
      }
      ()
    }
  }

}
