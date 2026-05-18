package izumi.distage.docker.bundled

import distage.{ModuleDef, TagKK}
import izumi.distage.docker.ContainerDef
import izumi.distage.docker.model.Docker.DockerPort

/**
  * Example AWS DynamoDB Local docker.
  * You're encouraged to use this definition as a template and modify it to your needs.
  */
object DynamoDocker extends ContainerDef {
  val primaryPort: DockerPort = DockerPort.TCP(8000)

  override def config: Config = {
    Config(
      registry = Some("public.ecr.aws"),
      image = "aws-dynamodb-local/aws-dynamodb-local:2.3.0",
      ports = Seq(primaryPort),
    )
  }
}

class DynamoDockerModule[F[+_, +_]: TagKK] extends ModuleDef {
  make[DynamoDocker.Container].fromResource {
    DynamoDocker.make[F]
  }
}

object DynamoDockerModule {
  def apply[F[+_, +_]: TagKK]: DynamoDockerModule[F] = new DynamoDockerModule[F]
}
