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
      image = "wurstmeister/kafka:1.0.0",
      ports = Seq(primaryPort),
      env = Map(
        "KAFKA_ADVERTISED_HOST_NAME" -> "127.0.0.1"
      ),
      networks = Seq(ZookeeperDocker.zookeeperNetwork),
      reuse = false
    )
  }
}

class KafkaDockerModule[F[_]: TagK] extends ModuleDef {
  make[KafkaDocker.Container].fromResource {
    (zookeeperDocker: ZookeeperDocker.Container, wr: DockerClientWrapper[F], log: IzLogger, eff: DIEffect[F], effAsync: DIEffectAsync[F]) =>
      val conf = zookeeperDocker.containerConfig.name.fold(KafkaDocker.config) {
        name =>
          val zkEnv = KafkaDocker.config.env ++ Map("KAFKA_ZOOKEEPER_CONNECT" -> s"$name:2181")
          KafkaDocker.config.copy(env = zkEnv)
      }
      new DockerContainer.Resource[F, KafkaDocker.Tag](conf, wr, log)(eff, effAsync)
  }
}
