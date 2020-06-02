package izumi.distage.docker.examples

import izumi.distage.docker.Docker.DockerPort
import izumi.distage.docker.{ContainerDef, healthcheck}
import izumi.distage.model.definition.ModuleDef
import izumi.reflect.TagK

object PostgresDocker extends ContainerDef {
  val primaryPort: DockerPort = DockerPort.TCP(5432)

  override def config: Config = {
    Config(
      image = "library/postgres:12.2",
      ports = Seq(primaryPort),
      env = Map("POSTGRES_PASSWORD" -> "postgres"),
      healthCheck = healthcheck.withPostgresProtocolCheck,
    )
  }
}

class PostgresDockerModule[F[_]: TagK] extends ModuleDef {
  make[PostgresDocker.Container].fromResource {
    PostgresDocker.make[F]
  }
}

object PostgresDockerModule {
  def apply[F[_]: TagK]: PostgresDockerModule[F] = new PostgresDockerModule[F]
}
