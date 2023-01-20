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

object KafkaTwofaceDocker extends ContainerDef {
  val insidePort: DockerPort = DockerPort.DynamicTCP("dynamic_kafka_port_inside")
  val outsidePort: DockerPort = DockerPort.DynamicTCP("dynamic_kafka_port_outside")

  private[this] def portVars: String = Seq(
    s"""KAFKA_LISTENERS=INSIDE://:$$${insidePort.toEnvVariable},OUTSIDE://:$$${outsidePort.toEnvVariable}""",
    s"""KAFKA_ADVERTISED_LISTENERS=INSIDE://:$$${insidePort.toEnvVariable},OUTSIDE://127.0.0.1:$$${outsidePort.toEnvVariable}""",
    """KAFKA_INTER_BROKER_LISTENER_NAME=INSIDE""",
    """KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=INSIDE:PLAINTEXT,OUTSIDE:PLAINTEXT""",
  ).map(defn => s"export $defn").mkString("; ")

  override def config: Config = {
    Config(
      image = "wurstmeister/kafka:2.12-2.4.1",
      ports = Seq(insidePort, outsidePort),
      entrypoint = Seq("sh", "-c", s"$portVars ; start-kafka.sh"),
    )
  }
}

class KafkaDockerModule[F[_]: TagK] extends ModuleDef {
  make[KafkaDocker.Container].fromResource {
    KafkaDocker
      .make[F]
      .connectToNetwork(KafkaZookeeperNetwork)
      .dependOnContainerPorts(ZookeeperDocker)(2181 -> "KAFKA_ZOOKEEPER_CONNECT")
  }

  make[KafkaTwofaceDocker.Container].named("twoface").fromResource {
    KafkaTwofaceDocker
      .make[F]
      .connectToNetwork(KafkaZookeeperNetwork)
      .dependOnContainerPorts(ZookeeperDocker)(2181 -> "KAFKA_ZOOKEEPER_CONNECT")
  }
}

object KafkaDockerModule {
  def apply[F[_]: TagK]: KafkaDockerModule[F] = new KafkaDockerModule[F]
}
