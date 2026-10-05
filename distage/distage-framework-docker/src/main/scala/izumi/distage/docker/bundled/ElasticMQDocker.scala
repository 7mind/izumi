package izumi.distage.docker.bundled

import izumi.distage.docker.ContainerDefTemplate
import izumi.distage.docker.model.Docker.DockerPort
import izumi.distage.docker.healthcheck.ContainerHealthCheck
import izumi.distage.model.definition.ModuleDef
import izumi.reflect.TagK

/**
  * Template for creating customized ElasticMQ docker containers.
  *
  * {{{
  * object MyElasticMQ extends ElasticMQDockerTemplateDef {
  *   override def version: String = "1.4.0"
  * }
  *
  * // in ModuleDef:
  * make[MyElasticMQ.Container].fromResource(MyElasticMQ.make[F])
  * }}}
  *
  * @see [[ElasticMQDocker]] for a ready-to-use default instance
  */
trait ElasticMQDockerTemplateDef extends ContainerDefTemplate {
  self: Singleton =>

  override def image: String = "softwaremill/elasticmq-native"
  override def version: String = "1.6.14"

  val primaryPort: DockerPort = DockerPort.TCP(9324)

  override def config: Config = {
    Config(
      image = s"$image:$version",
      ports = Seq(primaryPort),
      healthCheck = ContainerHealthCheck.httpGetCheck(primaryPort),
    )
  }
}

object ElasticMQDocker extends ElasticMQDockerTemplateDef

class ElasticMQDockerModule[F[_]: TagK] extends ModuleDef {
  make[ElasticMQDocker.Container].fromResource {
    ElasticMQDocker.make[F]
  }
}

object ElasticMQDockerModule {
  def apply[F[_]: TagK]: ElasticMQDockerModule[F] = new ElasticMQDockerModule[F]
}
