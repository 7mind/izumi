package izumi.distage.docker.bundled

import distage.{Id, ModuleDef, TagK}
import izumi.distage.docker.Docker.{DockerPort, DockerReusePolicy, Mount}
import izumi.distage.docker.healthcheck.ContainerHealthCheck
import izumi.distage.docker.{ContainerDef, ContainerNetworkDef}

object PostgresFlyWayDocker extends ContainerDef {
  /** @param flyWaySqlPath path to the migrations directory, by default `/sql` in current resource directory if exists */
  final case class Cfg(
    flyWaySqlPath: String = Cfg.defaultMigrationsResource,
    user: String = "postgres",
    password: String = "postgres",
    database: String = "postgres",
    schema: String = "public",
  )
  object Cfg {
    lazy val defaultMigrationsResource: String = Option(classOf[Cfg].getResource("/sql")).fold("")(_.getPath)
    lazy val default: Cfg = Cfg()
  }

  val primaryPort: DockerPort = DockerPort.TCP(5432)

  def applyCfg(cfg: Cfg): Config => Config = _.copy(
    env = Map("POSTGRES_PASSWORD" -> cfg.password),
    healthCheck = ContainerHealthCheck.postgreSqlProtocolCheck(primaryPort, cfg.user, cfg.password),
  )

  override def config: Config = applyCfg(Cfg.default)(
    Config(
      image = "library/postgres:12.3",
      ports = Seq(primaryPort),
    )
  )

  object FlyWay extends ContainerDef {
    def applyCfg(hostname: String, cfg: Cfg): Config => Config = _.copy(
      cmd = Seq(
        s"-url=jdbc:postgresql://$hostname/${cfg.database}",
        s"-schemas=${cfg.schema}",
        s"-user=${cfg.user}",
        s"-password=${cfg.password}",
        "-connectRetries=60",
        "baseline",
        "migrate",
      ),
      mounts = Seq(Mount(cfg.flyWaySqlPath, "/flyway/sql")),
    )

    override def config: Config = applyCfg("localhost", Cfg(""))(
      Config(
        image = "flyway/flyway:6.0-alpine",
        ports = Seq.empty,
        healthCheck = ContainerHealthCheck.exited(canBeDestroyed = true),
        reuse = DockerReusePolicy.ReuseEnabled,
        autoRemove = false,
      )
    )
  }

  object FlyWayNetwork extends ContainerNetworkDef {
    override def config: Config = Config()
  }

}

/**
  * By default [[PostgresFlyWayDocker]] will mount the `resources/sql` directory in the target docker,
  * however, this cannot work if `resources` folder is virtual, i.e. inside the JAR - this module will only work
  * in development mode when resources are represented by files on the filesystem.
  *
  * If you need to run [[PostgresFlyWayDocker]] in a JAR, please use a custom `cfg` parameter
  *
  * @param cfg Config with flyway migrations path
  */
class PostgresFlyWayDockerModule[F[_]: TagK](
  cfg: => PostgresFlyWayDocker.Cfg = PostgresFlyWayDocker.Cfg()
) extends ModuleDef {
  make[PostgresFlyWayDocker.Cfg].from(cfg)
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
      .modifyConfig(PostgresFlyWayDocker.applyCfg _)
  }
  // FlyWay container binding with modification of container parameters (to pass Postgres container address into config).
  make[PostgresFlyWayDocker.FlyWay.Container].fromResource {
    PostgresFlyWayDocker.FlyWay
      .make[F]
      .connectToNetwork(PostgresFlyWayDocker.FlyWayNetwork)
      .modifyConfig {
        (postgresContainer: PostgresFlyWayDocker.Container @Id("postgres-flyway-proxy"), cfg: PostgresFlyWayDocker.Cfg) => config =>
          PostgresFlyWayDocker.FlyWay.applyCfg(postgresContainer.hostName, cfg)(config)
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
