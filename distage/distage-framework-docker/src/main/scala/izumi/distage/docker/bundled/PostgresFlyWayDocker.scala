package izumi.distage.docker.bundled

import distage.{Functoid, Id, Lifecycle, ModuleDef, TagK}
import izumi.distage.docker.model.Docker.{ContainerFile, DockerPort, DockerReusePolicy}
import izumi.distage.docker.healthcheck.ContainerHealthCheck
import izumi.distage.docker.{ContainerDef, ContainerHooks, ContainerNetworkDef}
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.resources.IzResources
import izumi.fundamentals.platform.resources.IzResources.MaterializedResource

import java.nio.file.Paths

/**
  * Example postgres docker with flyway. It's sufficient to apply simple migrations on start.
  * You're encouraged to use this definition as a template and modify it to your needs.
  */
object PostgresFlyWayDocker extends ContainerDef {

  /** @param flyWaySqlPath directory with the migrations; `None` mounts the `sql` resource directory of the classpath */
  final case class Cfg(
    flyWaySqlPath: Option[String] = None,
    user: String = "postgres",
    password: String = "postgres",
    database: String = "postgres",
    schema: String = "public",
  )
  object Cfg {
    lazy val default: Cfg = Cfg()
  }

  val primaryPort: DockerPort = DockerPort.TCP(5432)

  def applyCfg(cfg: Cfg): Config => Config = c =>
    c.copy(
      env = c.env ++ Map(
        "POSTGRES_USER" -> cfg.user,
        "POSTGRES_PASSWORD" -> cfg.password,
        "POSTGRES_DB" -> cfg.database,
      ),
      healthCheck = ContainerHealthCheck.postgreSqlProtocolCheck(primaryPort, cfg.user, cfg.password),
    )

  override def config: Config = PostgresFlyWayDocker.applyCfg(Cfg.default)(
    Config(
      registry = Some("public.ecr.aws"),
      image = PostgresDocker.config.image,
      ports = Seq(primaryPort),
    )
  )

  object FlyWay extends ContainerDef {
    final case class Migrations(directory: MaterializedResource)

    def migrations(cfg: Cfg): Lifecycle[Identity, Migrations] = {
      Lifecycle.makeSimple {
        val directory = cfg.flyWaySqlPath match {
          case Some(path) =>
            MaterializedResource.InPlace(Paths.get(path))
          case None =>
            IzResources(classOf[Cfg])
              .materialize("sql", "flyway-sql")
              .getOrElse(throw new IllegalStateException("No `sql` resource directory on the classpath; set `flyWaySqlPath` to the migrations directory"))
        }
        Migrations(directory)
      }(_.directory.close())
    }

    def applyCfg(hostname: String, cfg: Cfg, migrations: Migrations): Config => Config = _.copy(
      cmd = Seq(
        s"-url=jdbc:postgresql://$hostname/${cfg.database}",
        s"-schemas=${cfg.schema}",
        s"-user=${cfg.user}",
        s"-password=${cfg.password}",
        "-connectRetries=60",
        "baseline",
        "migrate",
      ),
      afterCreate = Seq(ContainerHooks.copyFiles(ContainerFile(migrations.directory.path, "/flyway/sql"))),
    )

    override def config: Config = Config(
      image = "flyway/flyway:10",
      ports = Seq.empty,
      reuse = DockerReusePolicy.ReuseEnabled,
      autoRemove = false,
      healthCheck = ContainerHealthCheck.exitCodeCheck(),
    )
  }

  object FlyWayNetwork extends ContainerNetworkDef {
    override def config: Config = Config()
  }

}

/**
  * By default [[PostgresFlyWayDocker]] will copy the `resources/sql` directory into the target docker.
  * When `resources` is packaged inside a JAR, the `sql` directory is extracted into a temporary
  * directory for the lifetime of the FlyWay container, which is then copied instead.
  *
  * To copy a different directory, set `flyWaySqlPath` in the `cfg` parameter
  *
  * @param cfg Config with flyway migrations path
  */
class PostgresFlyWayDockerModule[F[_]: TagK](
  cfg: => PostgresFlyWayDocker.Cfg = PostgresFlyWayDocker.Cfg()
) extends ModuleDef {

  make[PostgresFlyWayDocker.Cfg].from(cfg)

  make[PostgresFlyWayDocker.FlyWay.Migrations].fromResource {
    (cfg: PostgresFlyWayDocker.Cfg) => PostgresFlyWayDocker.FlyWay.migrations(cfg)
  }

  // Network binding, to be able to access Postgres container from the FlyWay container
  make[PostgresFlyWayDocker.FlyWayNetwork.Network].fromResource {
    PostgresFlyWayDocker.FlyWayNetwork.make[F]
  }

  // Binding of the Postgres container.
  // Here we are going to bind the proxy instance so that we setup postgres DB before flyway,
  // later we'll just return the running container here as the real instance, after FlyWay container has run.
  make[PostgresFlyWayDocker.Container].named("postgres-flyway-proxy").fromResource {
    PostgresFlyWayDocker
      .make[F]
      .connectToNetwork(PostgresFlyWayDocker.FlyWayNetwork)
      .modifyConfig {
        (cfg: PostgresFlyWayDocker.Cfg) =>
          PostgresFlyWayDocker.applyCfg(cfg)
      }
  }

  // FlyWay container binding with modification of container parameters (to pass Postgres container address into config).
  make[PostgresFlyWayDocker.FlyWay.Container].fromResource {
    PostgresFlyWayDocker.FlyWay
      .make[F]
      .connectToNetwork(PostgresFlyWayDocker.FlyWayNetwork)
      .modifyConfig {
        Functoid { // FIXME: explicit `Functoid` application required on Scala 3 due to https://github.com/lampepfl/dotty/issues/16108
          (
            postgresContainer: PostgresFlyWayDocker.Container @Id(name = "postgres-flyway-proxy"),
            cfg: PostgresFlyWayDocker.Cfg,
            migrations: PostgresFlyWayDocker.FlyWay.Migrations,
          ) =>
            PostgresFlyWayDocker.FlyWay.applyCfg(postgresContainer.hostName, cfg, migrations)
        }
      }
  }

  // Binding of the actual Postgres container with the FlyWay container dependency.
  make[PostgresFlyWayDocker.Container]
    .using[PostgresFlyWayDocker.Container]("postgres-flyway-proxy")
    .addDependency[PostgresFlyWayDocker.FlyWay.Container]

}

object PostgresFlyWayDockerModule {
  def apply[F[_]: TagK](cfg: => PostgresFlyWayDocker.Cfg = PostgresFlyWayDocker.Cfg()): PostgresFlyWayDockerModule[F] = new PostgresFlyWayDockerModule[F](cfg)
}
