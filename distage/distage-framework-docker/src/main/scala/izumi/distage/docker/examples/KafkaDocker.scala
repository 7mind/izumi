package izumi.distage.docker.examples

import distage.{ModuleDef, TagK}
import izumi.distage.docker.Docker.{ContainerConfig, DockerPort}
import izumi.distage.docker.{ContainerDef, DockerClientWrapper, DockerContainer}
import izumi.distage.model.effect.{DIEffect, DIEffectAsync}
import izumi.logstage.api.IzLogger

object KafkaDocker extends ContainerDef {
  val primaryPort: DockerPort.TCP = DockerPort.TCP(9092)
  override def config: Config = {
    ContainerConfig(
      image = "wurstmeister/kafka:latest",
      ports = Seq(primaryPort),
      env = Map(
        "KAFKA_ADVERTISED_HOST_NAME" -> "127.0.0.1"
      ),
      networks = Seq(ZookeeperDocker.zookeeperNetwork),
    )
  }
}

class KafkaDockerModule[F[_]: TagK] extends ModuleDef {
  make[KafkaDocker.Container].fromResource {
    (zookeeperDocker: ZookeeperDocker.Container, wr: DockerClientWrapper[F], log: IzLogger, eff: DIEffect[F], effAsync: DIEffectAsync[F]) =>
      val zkEnv = KafkaDocker.config.env ++ Map("KAFKA_ZOOKEEPER_CONNECT" -> s"${zookeeperDocker.hostName}:2181")
      new DockerContainer.Resource[F, KafkaDocker.Tag](KafkaDocker.config.copy(env = zkEnv), wr, log)(eff, effAsync)
  }
}
