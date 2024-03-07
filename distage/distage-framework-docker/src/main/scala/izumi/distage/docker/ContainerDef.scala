package izumi.distage.docker

import distage.TagK
import izumi.distage.docker.DockerContainer.DependencyTag
import izumi.distage.docker.impl.ContainerResource
import izumi.distage.model.definition.Lifecycle
import izumi.distage.model.definition.dsl.ModuleDefDSL
import izumi.distage.model.providers.Functoid
import izumi.fundamentals.platform.language.Quirks.*

trait ContainerDef {
  // `ContainerDef`s must be top-level objects, otherwise `.Container` and `.Config` won't be referencable in ModuleDef
  self: Singleton =>

  type Tag

  final type Container = DockerContainer[Tag]

  final type Config = Docker.ContainerConfig[Tag]
  final lazy val Config = Docker.ContainerConfig

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
    *     .dependOnContainer(ZookeeperDocker)
    * }
    * }}}
    *
    * To kill all containers spawned by distage, use the following command:
    *
    * {{{
    *   docker rm -f $(docker ps -q -a -f 'label=distage.type')
    * }}}
    */
  final def make[F[_]: TagK](
    implicit tag: distage.Tag[Tag],
    mutateModule: ModuleDefDSL#MutationContext,
  ): Functoid[ContainerResource[F, Tag] & Lifecycle[F, Container]] = {
    tag.discard()
    new mutateModule.dsl {
      many[DockerContainer[Any]].named(DependencyTag.get[Tag])
    }
    val f: Functoid[ContainerResource[F, Tag] & Lifecycle[F, Container]] = DockerContainer.resource[F](this)
    f.annotateParameter[Set[DockerContainer[Any]]](DependencyTag.get[Tag])
  }

  final def copy(config: Config): ContainerDef.Aux[self.Tag] = {
    @inline def c = config
    object copy extends ContainerDef {
      override type Tag = self.Tag
      override def config: Config = c
    }
    copy
  }
}

object ContainerDef {
  type Aux[T] = ContainerDef { type Tag = T }
}
