package izumi.distage.docker.bundled

import distage.Id
import izumi.distage.docker.Docker.{DockerPort, DockerReusePolicy, Mount}
import izumi.distage.docker.healthcheck.ContainerHealthCheck
import izumi.distage.docker.{ContainerDef, ContainerNetworkDef}
import izumi.distage.model.definition.ModuleDef
import izumi.reflect.TagK

object PostgresFlyWayDocker extends ContainerDef {
  val primaryPort: DockerPort = DockerPort.TCP(5432)

  override def config: Config = {
    Config(
      image = "library/postgres:12.3",
      ports = Seq(primaryPort),
      env = Map("POSTGRES_PASSWORD" -> "postgres"),
      healthCheck = ContainerHealthCheck.postgreSqlProtocolCheck(primaryPort, "postgres", "postgres"),
    )
  }

  object FlyWay extends ContainerDef {
    override def config: Config = Config(
      image = "flyway/flyway:6.0-alpine",
      ports = Seq.empty,
      cmd = Seq(
        "-url=jdbc:postgresql://localhost/postgres",
        "-schemas=public",
        "-user=postgres",
        "-password=postgres",
        "-connectRetries=60",
        "baseline",
        "migrate",
      ),
      mounts = Seq(Mount(this.getClass.getClassLoader.getResource("sql").getPath, "/flyway/sql")),
      healthCheck = ContainerHealthCheck.succeed,
      reuse = DockerReusePolicy.ReuseEnabled,
      autoRemove = false,
    )
  }

  object FlyWayNetwork extends ContainerNetworkDef {
    override def config: Config = Config()
  }
}

class PostgresFlyWayDockerModule[F[_]: TagK] extends ModuleDef {
  // Network binding, to be able to access Postgres container from the FlyWay container
  make[PostgresFlyWayDocker.FlyWayNetwork.Network].fromResource {
    PostgresFlyWayDocker.FlyWayNetwork.make[F]
  }
  // Binding of the Postgres container.
  // Here we are going to bind the proxy instance, to create default instance later with FlyWay container dependency.
  make[PostgresFlyWayDocker.Container].named("postgres-flyway-proxy").fromResource {
    PostgresFlyWayDocker
      .make[F]
      .connectToNetwork(PostgresFlyWayDocker.FlyWayNetwork)
  }
  // FlyWay container binding with modification of container parameters (to pass Postgres container address into config).
  make[PostgresFlyWayDocker.FlyWay.Container].fromResource {
    PostgresFlyWayDocker
      .FlyWay
      .make[F]
      .connectToNetwork(PostgresFlyWayDocker.FlyWayNetwork)
      .modifyConfig {
        (postgresFlyWayDocker: PostgresFlyWayDocker.Container @Id("postgres-flyway-proxy")) => old: PostgresFlyWayDocker.FlyWay.Config =>
          val postgresCmd = old.cmd.filterNot(_.contains("url")) ++ Seq(s"-url=jdbc:postgresql://${postgresFlyWayDocker.hostName}/postgres")
          old.copy(cmd = postgresCmd)
      }
  }
  // Binding of the actual Postgres container with the FlyWay container dependency.
  make[PostgresFlyWayDocker.Container].from {
    (postgresFlyWayDocker: PostgresFlyWayDocker.Container @Id("postgres-flyway-proxy"), _: PostgresFlyWayDocker.FlyWay.Container) =>
      postgresFlyWayDocker
  }
}

object PostgresFlyWayDockerModule {
  def apply[F[_]: TagK]: PostgresFlyWayDockerModule[F] = new PostgresFlyWayDockerModule[F]
}
