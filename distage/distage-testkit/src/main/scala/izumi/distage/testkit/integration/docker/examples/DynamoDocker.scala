package izumi.distage.testkit.integration.docker.examples

import distage.{ModuleDef, TagK}
import izumi.distage.testkit.integration.docker.ContainerDef
import izumi.distage.testkit.integration.docker.Docker.{ContainerConfig, DockerPort}

object DynamoDocker extends ContainerDef {
  val primaryPort: DockerPort = DockerPort.TCP(8000)

  override def config: Config = {
    ContainerConfig(
      image = "amazon/dynamodb-local:latest",
      ports = Seq(primaryPort),
    )
  }
}

class DynamoDockerModule[F[_]: TagK] extends ModuleDef {
  make[DynamoDocker.Container].fromResource {
    DynamoDocker.make[F]
  }
}
