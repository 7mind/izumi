package izumi.distage.docker.examples

import distage.{ModuleDef, TagK}
import izumi.distage.docker.ContainerDef
import izumi.distage.docker.Docker.DockerPort

object DynamoDocker extends ContainerDef {
  val primaryPort: DockerPort = DockerPort.TCP(8000)

  override def config: Config = {
    Config(
      image = "amazon/dynamodb-local:1.12.0",
      ports = Seq(primaryPort),
    )
  }
}

class DynamoDockerModule[F[_]: TagK] extends ModuleDef {
  make[DynamoDocker.Container].fromResource {
    DynamoDocker.make[F]
  }
}
