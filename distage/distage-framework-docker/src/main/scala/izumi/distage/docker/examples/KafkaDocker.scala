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
      image = "wurstmeister/kafka:2.12-2.4.1",
      ports = Seq(primaryPort),
      env = Map(
        "KAFKA_ADVERTISED_HOST_NAME" -> "127.0.0.1"
      ),
      reuse = false
    )
  }
}

class KafkaDockerModule[F[_]: TagK] extends ModuleDef {
  make[KafkaDocker.Container].fromResource {
    (zookeeperDocker: ZookeeperDocker.Container, net: KafkaZookeeperNetwork.Network, wr: DockerClientWrapper[F], log: IzLogger, eff: DIEffect[F], effAsync: DIEffectAsync[F]) =>
      val zkEnv = KafkaDocker.config.env ++ Map("KAFKA_ZOOKEEPER_CONNECT" -> s"${zookeeperDocker.hostName}:2181")
      val zkNet = KafkaDocker.config.networks + net
      new DockerContainer.ContainerResource[F, KafkaDocker.Tag](KafkaDocker.config.copy(env = zkEnv, networks = zkNet), wr, log)(eff, effAsync)
  }
}
