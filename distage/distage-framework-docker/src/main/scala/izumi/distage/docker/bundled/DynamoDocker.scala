package izumi.distage.docker.bundled

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

object DynamoDockerModule {
  def apply[F[_]: TagK]: DynamoDockerModule[F] = new DynamoDockerModule[F]
}
