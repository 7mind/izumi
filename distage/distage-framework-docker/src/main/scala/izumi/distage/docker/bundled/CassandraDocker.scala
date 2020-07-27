package izumi.distage.docker.bundled

import izumi.distage.docker.ContainerDef
import izumi.distage.docker.Docker.DockerPort
import izumi.distage.model.definition.ModuleDef
import izumi.reflect.TagK

object CassandraDocker extends ContainerDef {
  val primaryPort: DockerPort = DockerPort.TCP(9042)

  override def config: Config = {
    Config(
      image = "cassandra:3.11.6",
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
