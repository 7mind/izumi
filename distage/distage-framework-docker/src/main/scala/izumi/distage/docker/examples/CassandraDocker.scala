package izumi.distage.docker.examples

import izumi.distage.docker.ContainerDef
import izumi.distage.docker.Docker.{ContainerConfig, DockerPort}
import izumi.distage.model.definition.ModuleDef
import izumi.fundamentals.reflection.Tags.TagK

object CassandraDocker extends ContainerDef {
  val primaryPort: DockerPort = DockerPort.TCP(9042)

  override def config: Config = {
    ContainerConfig(
      image = "cassandra:3.0.16",
      ports = Seq(primaryPort),
      env = Map(
        "CASSANDRA_VERSION" -> "3.0.16",
        "CASSANDRA_CONFIG" -> "/etc/cassandra"
      )
    )
  }
}

class CassandraDockerModule[F[_]: TagK] extends ModuleDef {
  make[CassandraDocker.Container].fromResource {
    CassandraDocker.make[F]
  }
}
