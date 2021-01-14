package izumi.distage.docker.bundled

import distage.{ModuleDef, TagK}
import izumi.distage.docker.ContainerDef
import izumi.distage.docker.Docker.DockerPort

object KafkaDocker extends ContainerDef {
  val primaryPort: DockerPort = DockerPort.DynamicTCP("dynamic_kafka_port")

  private[this] def portVars: String = Seq(
    s"""KAFKA_ADVERTISED_PORT=$$${primaryPort.toEnvVariable}""",
    s"""KAFKA_PORT=$$${primaryPort.toEnvVariable}""",
  ).map(defn => s"export $defn").mkString("; ")

  override def config: Config = {
    Config(
      image = "wurstmeister/kafka:2.12-2.4.1",
      ports = Seq(primaryPort),
      env = Map("KAFKA_ADVERTISED_HOST_NAME" -> "127.0.0.1"),
      entrypoint = Seq("sh", "-c", s"$portVars ; start-kafka.sh"),
    )
  }
}

class KafkaDockerModule[F[_]: TagK] extends ModuleDef {
  make[KafkaDocker.Container].fromResource {
    KafkaDocker
      .make[F]
      .connectToNetwork(KafkaZookeeperNetwork)
      .useDependencyPorts(ZookeeperDocker)(2181 -> "KAFKA_ZOOKEEPER_CONNECT")
  }
}

object KafkaDockerModule {
  def apply[F[_]: TagK]: KafkaDockerModule[F] = new KafkaDockerModule[F]
}
