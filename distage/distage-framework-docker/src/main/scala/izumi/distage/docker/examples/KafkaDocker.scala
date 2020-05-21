package izumi.distage.docker.examples

import distage.{ModuleDef, TagK}
import izumi.distage.docker.ContainerDef
import izumi.distage.docker.Docker.DockerPort

object KafkaDocker extends ContainerDef {
  val primaryPort: DockerPort.TCP = DockerPort.TCP(9092)
  override def config: Config = {
    Config(
      image = "wurstmeister/kafka:2.12-2.4.1",
      ports = Seq(primaryPort),
      env = Map(
        "KAFKA_ADVERTISED_HOST_NAME" -> "127.0.0.1"
      ),
    )
  }
}

class KafkaDockerModule[F[_]: TagK] extends ModuleDef {
  make[KafkaDocker.Container].fromResource {
    KafkaDocker
      .make[F]
      .modifyConfig {
        (zookeeperDocker: ZookeeperDocker.Container, net: KafkaZookeeperNetwork.Network) => old: KafkaDocker.Config =>
          val zkEnv = old.env ++ Map("KAFKA_ZOOKEEPER_CONNECT" -> s"${zookeeperDocker.hostName}:2181")
          val zkNet = old.networks + net
          old.copy(env = zkEnv, networks = zkNet)
      }
  }
}

object KafkaDockerModule {
  def apply[F[_]: TagK]: KafkaDockerModule[F] = new KafkaDockerModule[F]
}
