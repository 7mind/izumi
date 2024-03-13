package izumi.distage.docker.bundled

import izumi.distage.docker.ContainerDef
import izumi.distage.docker.model.Docker.DockerPort
import izumi.distage.docker.healthcheck.ContainerHealthCheck
import izumi.distage.model.definition.ModuleDef
import izumi.reflect.TagK

/**
  * Example Elastic MQ docker.
  * You're encouraged to use this definition as a template and modify it to your needs.
  */
object ElasticMQDocker extends ContainerDef {
  val primaryPort: DockerPort = DockerPort.TCP(9324)

  override def config: Config = {
    Config(
      image = "softwaremill/elasticmq:1.3.14",
      ports = Seq(primaryPort),
      healthCheck = ContainerHealthCheck.httpGetCheck(primaryPort),
    )
  }
}

class ElasticMQDockerModule[F[_]: TagK] extends ModuleDef {
  make[ElasticMQDocker.Container].fromResource {
    ElasticMQDocker.make[F]
  }
}

object ElasticMQDockerModule {
  def apply[F[_]: TagK]: ElasticMQDockerModule[F] = new ElasticMQDockerModule[F]
}
