package izumi.distage.docker.bundled

import izumi.distage.docker.{ContainerDefTemplate}
import izumi.distage.docker.model.Docker.DockerPort
import izumi.distage.docker.healthcheck.ContainerHealthCheck
import izumi.distage.model.definition.ModuleDef
import izumi.reflect.TagK

/**
  * Template for creating customized PostgreSQL docker containers.
  *
  * {{{
  * object MyPostgres extends PostgresDockerTemplateDef {
  *   override def version: String = "15"
  *   override def postgresUser: String = "myuser"
  *   override def postgresPassword: String = "secret"
  *   override def postgresDb: String = "mydb"
  * }
  *
  * // in ModuleDef:
  * make[MyPostgres.Container].fromResource(MyPostgres.make[F])
  * }}}
  *
  * @see [[PostgresDocker]] for a ready-to-use default instance
  */
trait PostgresDockerTemplateDef extends ContainerDefTemplate {
  self: Singleton =>

  override def image: String = "docker/library/postgres"
  override def version: String = "17"

  def postgresUser: String = "postgres"
  def postgresPassword: String = "postgres"
  def postgresDb: String = "postgres"

  val primaryPort: DockerPort = DockerPort.TCP(5432)

  override def config: Config = {
    Config(
      registry = Some("public.ecr.aws"),
      image = s"$image:$version",
      ports = Seq(primaryPort),
      env = Map(
        "POSTGRES_USER" -> postgresUser,
        "POSTGRES_PASSWORD" -> postgresPassword,
        "POSTGRES_DB" -> postgresDb,
      ),
      healthCheck = ContainerHealthCheck.postgreSqlProtocolCheck(primaryPort, postgresUser, postgresPassword),
    )
  }
}

object PostgresDocker extends PostgresDockerTemplateDef

class PostgresDockerModule[F[_]: TagK] extends ModuleDef {
  make[PostgresDocker.Container].fromResource {
    PostgresDocker.make[F]
  }
}

object PostgresDockerModule {
  def apply[F[_]: TagK]: PostgresDockerModule[F] = new PostgresDockerModule[F]
}
