package izumi.distage.testkit.integration.docker.examples

import izumi.distage.model.definition.ModuleDef
import izumi.distage.testkit.integration.docker.ContainerDef
import izumi.distage.testkit.integration.docker.Docker.{ContainerConfig, DockerPort}
import zio.Task

object PostgresDocker extends ContainerDef {
  val primaryPort: DockerPort = DockerPort.TCP(5432)

  override def config: Config = {
    ContainerConfig(
      image = "library/postgres:latest",
      ports = Seq(primaryPort),
    )
  }
}

class PostgresDockerModule extends ModuleDef {
  make[PostgresDocker.Container].fromResource {
    PostgresDocker.make[Task]
  }
}
