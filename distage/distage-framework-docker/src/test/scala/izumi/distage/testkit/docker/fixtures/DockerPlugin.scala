package izumi.distage.testkit.docker.fixtures

import distage.config.ConfigModuleDef
import izumi.distage.docker.bundled.*
import izumi.distage.docker.model.Docker.AvailablePort
import izumi.distage.docker.modules.DockerSupportModule
import izumi.distage.model.definition.Id
import izumi.distage.model.definition.StandardAxis.Mode
import izumi.distage.model.provisioning.IntegrationCheck
import izumi.distage.plugins.PluginDef
import izumi.fundamentals.platform.integration.{PortCheck, ResourceCheck}
import zio.Task

import scala.concurrent.duration.*

class PgSvcExample(
  val pg: AvailablePort @Id("pg"),
  val ddb: AvailablePort @Id("ddb"),
  val kafka: AvailablePort @Id("kafka"),
  val kafkaKraft: AvailablePort @Id("kafka-kraft"),
  val kafkaTwoFace: AvailablePort @Id("kafka-twoface"),
  val cs: AvailablePort @Id("cs"),
  val mq: AvailablePort @Id("mq"),
  val pgfw: AvailablePort @Id("pgfw"),
  val cmd: ReusedOneshotContainer.Container,
) extends IntegrationCheck[Task] {
  override def resourcesAvailable(): Task[ResourceCheck] = Task.effect {
    val portCheck = new PortCheck(50.milliseconds)
    portCheck.check(pg)
    portCheck.check(pgfw)
  }
}

object DockerPlugin extends PluginDef {
  include(DockerSupportModule[Task])

  make[DynamoDocker.Container].fromResource {
    DynamoDocker.make[Task]
  }

  include(CassandraDockerModule[Task])
  include(ZookeeperDockerModule[Task])
  include(KafkaDockerModule[Task])
  include(ElasticMQDockerModule[Task])
  include(CmdContainerModule[Task])
  include(PostgresFlyWayDockerModule[Task]())

  make[AvailablePort].named("mq").tagged(Mode.Test).from {
    (cs: ElasticMQDocker.Container) =>
      cs.availablePorts.first(ElasticMQDocker.primaryPort)
  }

  make[AvailablePort].named("cs").tagged(Mode.Test).from {
    (cs: CassandraDocker.Container) =>
      cs.availablePorts.first(CassandraDocker.primaryPort)
  }

  make[AvailablePort].named("kafka").tagged(Mode.Test).from {
    (kafka: KafkaDocker.Container) =>
      kafka.availablePorts.first(KafkaDocker.primaryPort)
  }

  make[AvailablePort].named("kafka-kraft").tagged(Mode.Test).from {
    (kafka: KafkaKRaftDocker.Container) =>
      kafka.availablePorts.first(KafkaKRaftDocker.primaryPort)
  }

  make[AvailablePort].named("kafka-twoface").tagged(Mode.Test).from {
    (kafka: KafkaTwofaceDocker.Container @Id("twoface")) =>
      kafka.availablePorts.first(KafkaTwofaceDocker.outsidePort)
  }

  make[AvailablePort].named("pgfw").tagged(Mode.Test).from {
    (cs: PostgresFlyWayDocker.Container) =>
      cs.availablePorts.first(PostgresFlyWayDocker.primaryPort)
  }

  // this container will start once `DynamoContainer` is up and running
  make[PostgresDocker.Container].fromResource {
    PostgresDocker.make[Task].dependOnContainer(DynamoDocker)
  }

  // these lines are for test scope
  make[AvailablePort].named("pg").tagged(Mode.Test).from {
    (pg: PostgresDocker.Container) =>
      pg.availablePorts.first(PostgresDocker.primaryPort)
  }
  make[AvailablePort].named("ddb").tagged(Mode.Test).from {
    (dn: DynamoDocker.Container) =>
      dn.availablePorts.first(DynamoDocker.primaryPort)
  }

  // and this one is for production
  make[AvailablePort].named("pg").tagged(Mode.Prod).from {
    (pgPort: Int @Id("postgres.port")) =>
      AvailablePort.local(pgPort)
  }

  make[PgSvcExample]

  include(new ConfigModuleDef {
    makeConfigNamed[Int]("postgres.port")
  })
}
