package izumi.distage.docker.bundled

import izumi.distage.docker.ContainerDef
import izumi.distage.docker.model.Docker.DockerPort
import izumi.distage.model.definition.ModuleDef
import izumi.reflect.TagK

/**
  * Example Cassandra docker.
  * You're encouraged to use this definition as a template and modify it to your needs.
  */
object CassandraDocker extends ContainerDef {
  val primaryPort: DockerPort = DockerPort.TCP(9042)

  override def config: Config = {
    Config(
      registry = Some("public.ecr.aws"),
      image = "docker/library/cassandra:3.11.14",
      ports = Seq(primaryPort),
    )
  }
}

class CassandraDockerModule[F[_]: TagK] extends ModuleDef {
  make[CassandraDocker.Container].fromResource {
    CassandraDocker.make[F]
  }
}

object CassandraDockerModule {
  def apply[F[_]: TagK]: CassandraDockerModule[F] = new CassandraDockerModule[F]
}
