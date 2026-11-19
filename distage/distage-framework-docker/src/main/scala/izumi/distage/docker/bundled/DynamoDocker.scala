package izumi.distage.docker.bundled

import distage.{ModuleDef, TagK}
import izumi.distage.docker.ContainerDefTemplate
import izumi.distage.docker.model.Docker.DockerPort

/**
  * Template for creating customized AWS DynamoDB Local docker containers.
  *
  * {{{
  * object MyDynamo extends DynamoDockerTemplateDef {
  *   override def version: String = "2.4.0"
  * }
  *
  * // in ModuleDef:
  * make[MyDynamo.Container].fromResource(MyDynamo.make[F])
  * }}}
  *
  * @see [[DynamoDocker]] for a ready-to-use default instance
  */
trait DynamoDockerTemplateDef extends ContainerDefTemplate {
  self: Singleton =>

  override def image: String = "amazon/dynamodb-local"
  override def version: String = "3.3.0"

  val primaryPort: DockerPort = DockerPort.TCP(8000)

  override def config: Config = {
    Config(
      image = s"$image:$version",
      ports = Seq(primaryPort),
    )
  }
}

object DynamoDocker extends DynamoDockerTemplateDef

class DynamoDockerModule[F[_]: TagK] extends ModuleDef {
  make[DynamoDocker.Container].fromResource {
    DynamoDocker.make[F]
  }
}

object DynamoDockerModule {
  def apply[F[_]: TagK]: DynamoDockerModule[F] = new DynamoDockerModule[F]
}
