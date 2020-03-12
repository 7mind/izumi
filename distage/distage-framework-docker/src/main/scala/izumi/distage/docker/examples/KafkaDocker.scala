package izumi.distage.docker.examples

import distage.{ModuleDef, TagK}
import izumi.distage.docker.ContainerDef
import izumi.distage.docker.Docker.{ContainerConfig, ContainerNetwork, DockerPort}

object KafkaDocker extends ContainerDef {
  val primaryPort: DockerPort.TCP = DockerPort.TCP(9092)
  override def config: Config = {
    ContainerConfig(
      image = "wurstmeister/kafka:1.0.0",
      ports = Seq(primaryPort),
      networks = Seq(ZookeeperDocker.zookeeperNetwork),
      env = Map(
        "KAFKA_ADVERTISED_HOST_NAME" -> "127.0.0.1",
        "KAFKA_ZOOKEEPER_CONNECT" -> "zookeeper-service:2181"
      )
    )
  }
}

class KafkaDockerModule[F[_]: TagK] extends ModuleDef {
  make[KafkaDocker.Container].fromResource {
    KafkaDocker.make[F].dependOnDocker(ZookeeperDocker)
  }
}
