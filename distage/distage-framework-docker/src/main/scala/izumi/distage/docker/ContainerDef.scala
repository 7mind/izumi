package izumi.distage.docker

import distage.TagK
import izumi.distage.docker.impl.ContainerResource
import izumi.distage.model.definition.Lifecycle
import izumi.distage.model.providers.Functoid

/**
  * Base trait for Docker container definitions.
  *
  * Must be extended by an `object` so that [[Tag]] becomes a unique path-dependent type
  * usable in `ModuleDef` bindings.
  *
  * For simple containers, extend `ContainerDef` directly:
  *
  * {{{
  * object MyRedis extends ContainerDef {
  *   val primaryPort: DockerPort = DockerPort.TCP(6379)
  *
  *   override def config: Config = Config(
  *     image = "redis:7",
  *     ports = Seq(primaryPort),
  *   )
  * }
  *
  * // in ModuleDef:
  * make[MyRedis.Container].fromResource(MyRedis.make[F])
  * }}}
  *
  * For common services, extend one of the bundled template classes
  * (e.g. [[bundled.PostgresDockerTemplateDef]], [[bundled.CassandraDockerTemplateDef]])
  * which provide sensible defaults and customization points:
  *
  * {{{
  * object MyPostgres extends PostgresDockerTemplateDef {
  *   override def version: String = "15"
  *   override def postgresPassword: String = "secret"
  * }
  * }}}
  *
  * To kill all containers spawned by distage, use the following command:
  *
  * {{{
  *   docker rm -f $(docker ps -q -a -f 'label=distage.type')
  * }}}
  *
  * @see [[bundled]]
  */
trait ContainerDef { self: Singleton =>

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
    implicit tag: distage.Tag[Tag]
  ): Functoid[ContainerResource[F, Tag] & Lifecycle[F, Container]] = {
    DockerContainer.resource[F](this)
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
