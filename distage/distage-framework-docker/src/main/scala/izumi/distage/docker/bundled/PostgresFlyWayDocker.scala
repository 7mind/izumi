package izumi.distage.docker.bundled

import distage.Id
import izumi.distage.docker.Docker.{DockerPort, DockerReusePolicy, Mount}
import izumi.distage.docker.healthcheck.ContainerHealthCheck
import izumi.distage.docker.{ContainerDef, ContainerNetworkDef}
import izumi.distage.model.definition.ModuleDef
import izumi.reflect.TagK

class PostgresFlyWayDocker(
  user: String,
  password: String,
  database: String,
  flyWaySqlPath: String,
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

  def module[F[_]: TagK](implicit ev0: distage.Tag[self.Tag]): ModuleDef = new ModuleDef {
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
