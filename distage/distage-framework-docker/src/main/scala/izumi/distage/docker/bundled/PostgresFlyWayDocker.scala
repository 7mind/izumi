package izumi.distage.docker.bundled

import distage.{Id, ModuleDef, TagK}
import izumi.distage.docker.Docker.{DockerPort, DockerReusePolicy, Mount}
import izumi.distage.docker.healthcheck.ContainerHealthCheck
import izumi.distage.docker.{ContainerDef, ContainerNetworkDef}

/**
  * Note: default [[PostgresFlyWayDocker]] will mount the `resources/sql` directory in the target docker,
  * however, this cannot work if `resources` folder is virtual, inside the JAR, i.e. this module will only work
  * in development mode when resources are files on the filesystem.
  * If you need to use [[PostgresFlyWayDocker]] with JARs, please instantiate class [[PostgresFlyWayDocker]]
  * with a custom `flywaySqlPath` parameter
  */
object PostgresFlyWayDocker
  extends PostgresFlyWayDocker(
    flyWaySqlPath = classOf[PostgresFlyWayDocker].getResource("/sql").getPath,
    user = "postgres",
    password = "postgres",
    database = "postgres",
    schema = "public",
  )

object PostgresFlyWayDockerModule {
  def apply[F[_]: TagK]: ModuleDef = PostgresFlyWayDocker.module[F]
  def apply[F[_]: TagK](
    flyWaySqlPath: String,
    user: String = "postgres",
    password: String = "postgres",
    database: String = "postgres",
    schema: String = "public",
  ): ModuleDef = new PostgresFlyWayDocker(flyWaySqlPath, user, password, database, schema).module[F]
}

/**
  * @param flyWaySqlPath path to the migrations directory
  */
class PostgresFlyWayDocker(
  flyWaySqlPath: String,
  user: String = "postgres",
  password: String = "postgres",
  database: String = "postgres",
  schema: String = "public",
) extends ContainerDef { self =>
  val primaryPort: DockerPort = DockerPort.TCP(5432)

  override def config: Config = {
    Config(
      image = "library/postgres:12.3",
      ports = Seq(primaryPort),
      env = Map("POSTGRES_PASSWORD" -> password),
      healthCheck = ContainerHealthCheck.postgreSqlProtocolCheck(primaryPort, user, password),
    )
  }

  def module[F[_]: TagK](implicit ev: distage.Tag[self.Tag]): ModuleDef = new ModuleDef {
    // Network binding, to be able to access Postgres container from the FlyWay container
    make[FlyWayNetwork.Network].fromResource {
      FlyWayNetwork.make[F]
    }
    // Binding of the Postgres container.
    // Here we are going to bind the proxy instance, to create default instance later with FlyWay container dependency.
    make[self.Container].named("postgres-flyway-proxy").fromResource {
      self
        .make[F]
        .connectToNetwork(FlyWayNetwork)
    }
    // FlyWay container binding with modification of container parameters (to pass Postgres container address into config).
    make[FlyWay.Container].fromResource {
      FlyWay
        .make[F]
        .connectToNetwork(FlyWayNetwork)
        .modifyConfig {
          (postgresFlyWayDocker: self.Container @Id("postgres-flyway-proxy")) => old: FlyWay.Config =>
            val postgresCmd = old.cmd.filterNot(_.contains("url")) ++ Seq(s"-url=jdbc:postgresql://${postgresFlyWayDocker.hostName}/$database")
            old.copy(cmd = postgresCmd)
        }
    }
    // Binding of the actual Postgres container with the FlyWay container dependency.
    make[self.Container].from {
      (postgresFlyWayDocker: self.Container @Id("postgres-flyway-proxy"), _: FlyWay.Container) =>
        postgresFlyWayDocker
    }
  }

  object FlyWay extends ContainerDef {
    override def config: Config = Config(
      image = "flyway/flyway:6.0-alpine",
      ports = Seq.empty,
      cmd = Seq(
        s"-url=jdbc:postgresql://localhost/$database",
        s"-schemas=$schema",
        s"-user=$user",
        s"-password=$password",
        "-connectRetries=60",
        "baseline",
        "migrate",
      ),
      mounts = Seq(Mount(flyWaySqlPath, "/flyway/sql")),
      healthCheck = ContainerHealthCheck.succeed,
      reuse = DockerReusePolicy.ReuseEnabled,
      autoRemove = false,
    )
  }

  object FlyWayNetwork extends ContainerNetworkDef {
    override def config: Config = Config()
  }
}
