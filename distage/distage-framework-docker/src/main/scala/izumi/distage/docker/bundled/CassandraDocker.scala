package izumi.distage.docker.bundled

import izumi.distage.docker.ContainerDefTemplate
import izumi.distage.docker.model.Docker.DockerPort
import izumi.distage.model.definition.ModuleDef
import izumi.reflect.TagK

/**
  * Template for creating customized Cassandra docker containers.
  *
  * {{{
  * object MyCassandra extends CassandraDockerTemplateDef {
  *   override def version: String = "4.1"
  * }
  *
  * // in ModuleDef:
  * make[MyCassandra.Container].fromResource(MyCassandra.make[F])
  * }}}
  *
  * @see [[CassandraDocker]] for a ready-to-use default instance
  */
trait CassandraDockerTemplateDef extends ContainerDefTemplate {
  self: Singleton =>

  override def image: String = "docker/library/cassandra"
  override def version: String = "5.0"

  val primaryPort: DockerPort = DockerPort.TCP(9042)

  override def config: Config = {
    Config(
      registry = Some("public.ecr.aws"),
      image = s"$image:$version",
      ports = Seq(primaryPort),
    )
  }
}

object CassandraDocker extends CassandraDockerTemplateDef

class CassandraDockerModule[F[_]: TagK] extends ModuleDef {
  make[CassandraDocker.Container].fromResource {
    CassandraDocker.make[F]
  }
}

object CassandraDockerModule {
  def apply[F[_]: TagK]: CassandraDockerModule[F] = new CassandraDockerModule[F]
}
