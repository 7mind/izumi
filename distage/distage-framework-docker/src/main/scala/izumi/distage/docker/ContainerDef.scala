package izumi.distage.docker

import distage.TagK
import izumi.distage.model.definition.Lifecycle
import izumi.distage.model.providers.Functoid
import izumi.fundamentals.platform.language.Quirks._

trait ContainerDef { self =>
  type Tag
  final type Container = DockerContainer[Tag]
  final type Config = Docker.ContainerConfig[Tag]
  final val Config = Docker.ContainerConfig

  def config: Config

  /**
    * For binding in `ModuleDef`:
    *
    * {{{
    * object KafkaDocker extends ContainerDef
    * object ZookeeperDocker extends ContainerDef
    *
    * make[KafkaDocker.Container].fromResource {
    *   KafkaDocker
    *     .make[F]
    *     .dependOnDocker(ZookeeperDocker)
    * }
    * }}}
    *
    * To kill all containers spawned by distage, use the following command:
    *
    * {{{
    *   docker rm -f $(docker ps -q -a -f 'label=distage.type')
    * }}}
    */
  final def make[F[_]: TagK](implicit tag: distage.Tag[Tag]): Functoid[ContainerResource[F, Tag] with Lifecycle[F, Container]] = {
    tag.discard()
    DockerContainer.resource[F](this)
  }

  final def copy(config: Config): ContainerDef.Aux[self.Tag] = {
    @inline def c = config
    new ContainerDef {
      override type Tag = self.Tag
      override def config: Config = c
    }
  }
}

object ContainerDef {
  type Aux[T] = ContainerDef { type Tag = T }
}
