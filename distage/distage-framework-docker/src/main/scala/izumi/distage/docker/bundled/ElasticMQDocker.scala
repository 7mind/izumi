package izumi.distage.docker.bundled

import izumi.distage.docker.ContainerDef
import izumi.distage.docker.Docker.DockerPort
import izumi.distage.docker.healthcheck.ContainerHealthCheck
import izumi.distage.model.definition.ModuleDef
import izumi.reflect.TagK

object ElasticMQDocker extends ContainerDef {
  val primaryPort: DockerPort = DockerPort.TCP(9324)

  override def config: Config = {
    Config(
      image = "softwaremill/elasticmq:0.15.7",
      ports = Seq(primaryPort),
      entrypoint = Seq("sh", "-c", s"/opt/docker/bin/elasticmq-server -Dnode-address.port=$$${primaryPort.toEnvVariable}"),
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
