package izumi.distage.docker.examples

import distage.{ModuleDef, TagK}
import izumi.distage.docker.ContainerDef
import izumi.distage.docker.Docker.DockerPort

object KafkaDocker extends ContainerDef {
  val primaryPort: DockerPort = DockerPort.DynamicTCP("dynamic_kafka_port")

  private[this] def portVars: String = Seq(
    s"""KAFKA_LISTENERS="PLAINTEXT://:$$${primaryPort.toEnvVariable}"""",
    s"""KAFKA_ADVERTISED_LISTENERS="PLAINTEXT://127.0.0.1:$$${primaryPort.toEnvVariable}"""",
  ).map(defn => s"export $defn").mkString("; ")

  override def config: Config = {
    Config(
      image = "wurstmeister/kafka:2.12-2.4.1",
      ports = Seq(primaryPort),
      entrypoint = Seq(
        "sh",
        "-c",
        s"""$portVars ; start-kafka.sh""",
      ),
    )
  }
}

class KafkaDockerModule[F[_]: TagK] extends ModuleDef {
  make[KafkaDocker.Container].fromResource {
    KafkaDocker
      .make[F]
      .connectToNetwork(KafkaZookeeperNetwork)
      .modifyConfig {
        zookeeperDocker: ZookeeperDocker.Container => old: KafkaDocker.Config =>
          val zkEnv = old.env ++ Map("KAFKA_ZOOKEEPER_CONNECT" -> s"${zookeeperDocker.hostName}:2181")
          old.copy(env = zkEnv)
      }
  }
}

object KafkaDockerModule {
  def apply[F[_]: TagK]: KafkaDockerModule[F] = new KafkaDockerModule[F]
}
