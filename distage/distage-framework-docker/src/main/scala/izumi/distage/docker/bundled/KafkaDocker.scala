package izumi.distage.docker.bundled

import distage.{ModuleDef, TagK}
import izumi.distage.docker.ContainerDef
import izumi.distage.docker.model.Docker.{ContainerEnvironment, DockerPort}

object KafkaDocker extends ContainerDef {
  val primaryPort: DockerPort = DockerPort.DynamicTCP("dynamic_kafka_port")

  override def config: Config = {
    Config(
      image = "wurstmeister/kafka:2.12-2.4.1",
      ports = Seq(primaryPort),
      env = ContainerEnvironment.from {
        ports =>
          val port = ports.getOrElse(primaryPort, "0000")
          Map(
            "KAFKA_ADVERTISED_HOST_NAME" -> "127.0.0.1",
            "KAFKA_ADVERTISED_PORT" -> port,
            "KAFKA_PORT" -> port,
          )
      },
    )
  }
}

object KafkaTwofaceDocker extends ContainerDef {
  val insidePort: DockerPort = DockerPort.DynamicTCP("dynamic_kafka_port_inside")
  val outsidePort: DockerPort = DockerPort.DynamicTCP("dynamic_kafka_port_outside")

  override def config: Config = {
    Config(
      image = "wurstmeister/kafka:2.12-2.4.1",
      ports = Seq(insidePort, outsidePort),
      env = ContainerEnvironment.from {
        ports =>
          val insidePortBinding = ports.getOrElse(insidePort, "0000")
          val outsidePortBinding = ports.getOrElse(insidePort, "0000")
          Map(
            "KAFKA_INTER_BROKER_LISTENER_NAME" -> "INSIDE",
            "KAFKA_LISTENER_SECURITY_PROTOCOL_MAP" -> "INSIDE:PLAINTEXT,OUTSIDE:PLAINTEXT",
            "KAFKA_LISTENERS" -> s"INSIDE://:$insidePortBinding,OUTSIDE://:$outsidePortBinding",
            "KAFKA_ADVERTISED_LISTENERS" -> s"INSIDE://:$insidePortBinding,OUTSIDE://127.0.0.1:$outsidePortBinding",
          )
      },
    )
  }
}

object KafkaKRaftDocker extends ContainerDef {
  val primaryPort: DockerPort = DockerPort.DynamicTCP("dynamic_kafka_port")

  override def config: Config = {
    Config(
      image = "bitnami/kafka:3.4.0",
      ports = Seq(primaryPort),
      env = ContainerEnvironment.from {
        ports =>
          val port = ports.getOrElse(primaryPort, "0000")
          Map(
            "KAFKA_CFG_PORT" -> port,
            "KAFKA_CFG_ADVERTISED_PORT" -> port,
            "KAFKA_CFG_LISTENERS" -> s"PLAINTEXT://:$port,CONTROLLER://:9093",
            "KAFKA_CFG_ADVERTISED_LISTENERS" -> s"PLAINTEXT://127.0.0.1:$port",
            "ALLOW_PLAINTEXT_LISTENER" -> "yes",
            "KAFKA_ENABLE_KRAFT" -> "yes",
            "KAFKA_BROKER_ID" -> "1",
            "KAFKA_CFG_ADVERTISED_HOST_NAME" -> "127.0.0.1",
            "KAFKA_CFG_PROCESS_ROLES" -> "broker,controller",
            "KAFKA_CFG_CONTROLLER_LISTENER_NAMES" -> "CONTROLLER",
            "KAFKA_CFG_CONTROLLER_QUORUM_VOTERS" -> "1@127.0.0.1:9093",
            "KAFKA_CFG_LISTENER_SECURITY_PROTOCOL_MAP" -> "CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT",
            "KAFKA_CFG_DELETE_TOPIC_ENABLE" -> "true",
          )
      },
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

  make[KafkaKRaftDocker.Container].fromResource {
    KafkaKRaftDocker.make[F]
  }
}

object KafkaDockerModule {
  def apply[F[_]: TagK]: KafkaDockerModule[F] = new KafkaDockerModule[F]
}
