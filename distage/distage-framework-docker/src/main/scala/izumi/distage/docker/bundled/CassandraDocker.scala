package izumi.distage.docker.bundled

import izumi.distage.docker.ContainerDef
import izumi.distage.docker.model.Docker.DockerPort
import izumi.distage.model.definition.ModuleDef
import izumi.reflect.TagKK

/**
  * Example Cassandra docker.
  * You're encouraged to use this definition as a template and modify it to your needs.
  */
object CassandraDocker extends ContainerDef {
  val primaryPort: DockerPort = DockerPort.TCP(9042)

  override def config: Config = {
    Config(
      registry = Some("public.ecr.aws"),
      image = "docker/library/cassandra:4.0",
      ports = Seq(primaryPort),
    )
  }
}

class CassandraDockerModule[F[+_, +_]: TagKK] extends ModuleDef {
  make[CassandraDocker.Container].fromResource {
    CassandraDocker.make[F]
  }
}

object CassandraDockerModule {
  def apply[F[+_, +_]: TagKK]: CassandraDockerModule[F] = new CassandraDockerModule[F]
}
