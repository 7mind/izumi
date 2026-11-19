package izumi.distage.docker.bundled

import distage.{ModuleDef, TagK}
import izumi.distage.docker.{ContainerDef, ContainerDefTemplate}
import izumi.distage.docker.model.Docker.{ContainerEnvironment, DockerPort}

/**
  * Template for creating customized Kafka (Zookeeper-based) docker containers.
  * Depends on [[KafkaZookeeperNetwork.Network]] provided by [[ZookeeperDocker]].
  *
  * {{{
  * object MyKafka extends KafkaDockerTemplateDef {
  *   override def version: String = "3.9.0"
  * }
  *
  * // in ModuleDef:
  * make[MyKafka.Container].fromResource {
  *   MyKafka.make[F]
  *     .connectToNetwork(KafkaZookeeperNetwork)
  *     .dependOnContainerPorts(ZookeeperDocker)(2181 -> "KAFKA_ZOOKEEPER_CONNECT")
  * }
  * }}}
  *
  * @see [[KafkaDocker]] for a ready-to-use default instance
  */
trait KafkaDockerTemplateDef extends ContainerDefTemplate {
  self: Singleton =>

  override def image: String = "apache/kafka"
  override def version: String = "3.9.0"

  val primaryPort: DockerPort = DockerPort.DynamicTCP("dynamic_kafka_port")

  override def config: Config = {
    Config(
      image = s"$image:$version",
      ports = Seq(primaryPort),
      env = ContainerEnvironment.from {
        ports =>
          val port = ports.getOrElse(primaryPort, "0000")
          Map(
            // apache/kafka image: env vars map to server.properties keys (KAFKA_<KEY> → <key.lower>)
            "KAFKA_NODE_ID" -> "1",
            "KAFKA_BROKER_ID" -> "1",
            "KAFKA_LISTENERS" -> s"PLAINTEXT://:$port",
            "KAFKA_ADVERTISED_LISTENERS" -> s"PLAINTEXT://127.0.0.1:$port",
            "KAFKA_LISTENER_SECURITY_PROTOCOL_MAP" -> "PLAINTEXT:PLAINTEXT",
            "KAFKA_INTER_BROKER_LISTENER_NAME" -> "PLAINTEXT",
            "KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR" -> "1",
          )
      },
    )
  }
}

object KafkaDocker extends KafkaDockerTemplateDef

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
          val outsidePortBinding = ports.getOrElse(outsidePort, "0000")
          Map(
            "KAFKA_INTER_BROKER_LISTENER_NAME" -> "INSIDE",
            "KAFKA_LISTENER_SECURITY_PROTOCOL_MAP" -> "INSIDE:PLAINTEXT,OUTSIDE:PLAINTEXT",
            "KAFKA_LISTENERS" -> s"INSIDE://:$insidePortBinding,OUTSIDE://:$outsidePortBinding",
            "KAFKA_ADVERTISED_LISTENERS" -> s"INSIDE://:$insidePortBinding,OUTSIDE://127.0.0.1:$outsidePortBinding",
          )
      },
      userTags = Map("kafka_container" -> "twofaced"),
    )
  }
}

/**
  * Template for creating customized Kafka KRaft (no Zookeeper) docker containers.
  *
  * {{{
  * object MyKafkaKRaft extends KafkaKRaftDockerTemplateDef {
  *   override def version: String = "4.1.0"
  * }
  *
  * // in ModuleDef:
  * make[MyKafkaKRaft.Container].fromResource(MyKafkaKRaft.make[F])
  * }}}
  *
  * @see [[KafkaKRaftDocker]] for a ready-to-use default instance
  */
trait KafkaKRaftDockerTemplateDef extends ContainerDefTemplate {
  self: Singleton =>

  override def image: String = "apache/kafka"
  override def version: String = "4.2.0"

  val primaryPort: DockerPort = DockerPort.DynamicTCP("dynamic_kafka_port")

  override def config: Config = {
    Config(
      image = s"$image:$version",
      ports = Seq(primaryPort),
      env = ContainerEnvironment.from {
        ports =>
          val port = ports.getOrElse(primaryPort, "0000")
          Map(
            // apache/kafka image: env vars map to server.properties keys (KAFKA_<KEY> → <key.lower>)
            "CLUSTER_ID" -> "5L6g3nShT-eMCtK--X86sw",
            "KAFKA_NODE_ID" -> "1",
            "KAFKA_PROCESS_ROLES" -> "broker,controller",
            "KAFKA_LISTENERS" -> s"PLAINTEXT://:$port,CONTROLLER://:9093",
            "KAFKA_ADVERTISED_LISTENERS" -> s"PLAINTEXT://127.0.0.1:$port",
            "KAFKA_CONTROLLER_LISTENER_NAMES" -> "CONTROLLER",
            "KAFKA_CONTROLLER_QUORUM_VOTERS" -> "1@localhost:9093",
            "KAFKA_LISTENER_SECURITY_PROTOCOL_MAP" -> "CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT",
            "KAFKA_INTER_BROKER_LISTENER_NAME" -> "PLAINTEXT",
            "KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR" -> "1",
            "KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR" -> "1",
            "KAFKA_TRANSACTION_STATE_LOG_MIN_ISR" -> "1",
          )
      },
    )
  }
}

object KafkaKRaftDocker extends KafkaKRaftDockerTemplateDef

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
