package izumi.distage.testkit.docker.fixtures

import distage.config.ConfigModuleDef
import izumi.distage.docker.Docker.AvailablePort
import izumi.distage.docker.examples._
import izumi.distage.docker.modules.DockerSupportModule
import izumi.distage.effect.modules.{CatsDIEffectModule, ZIODIEffectModule}
import izumi.distage.framework.model.IntegrationCheck
import izumi.distage.model.definition.Id
import izumi.distage.model.definition.StandardAxis.Env
import izumi.distage.plugins.PluginDef
import izumi.fundamentals.platform.integration.{PortCheck, ResourceCheck}
import zio.Task

import scala.concurrent.duration._

class PgSvcExample(
  val pg: AvailablePort @Id("pg"),
  val ddb: AvailablePort @Id("ddb"),
  val kafka: AvailablePort @Id("kafka"),
  val cs: AvailablePort @Id("cs"),
  val mq: AvailablePort @Id("mq"),
  val cmd: ReusedOneshotContainer.Container,
) extends IntegrationCheck {
  override def resourcesAvailable(): ResourceCheck = {
    new PortCheck(10.milliseconds).checkPort(pg.hostV4, pg.port)
  }
}

object MonadPlugin extends PluginDef with CatsDIEffectModule with ZIODIEffectModule

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

  make[AvailablePort].named("mq").tagged(Env.Test).from {
    cs: ElasticMQDocker.Container =>
      cs.availablePorts.get.first(ElasticMQDocker.primaryPort)
  }

  make[AvailablePort].named("cs").tagged(Env.Test).from {
    cs: CassandraDocker.Container =>
      cs.availablePorts.get.first(CassandraDocker.primaryPort)
  }

  make[AvailablePort].named("kafka").tagged(Env.Test).from {
    kafka: KafkaDocker.Container =>
      kafka.availablePorts.get.first(KafkaDocker.primaryPort)
  }

  // this container will start once `DynamoContainer` is up and running
  make[PostgresDocker.Container].fromResource {
    PostgresDocker.make[Task].dependOnDocker(DynamoDocker)
  }

  // these lines are for test scope
  make[AvailablePort].named("pg").tagged(Env.Test).from {
    pg: PostgresDocker.Container =>
      pg.availablePorts.get.first(PostgresDocker.primaryPort)
  }
  make[AvailablePort].named("ddb").tagged(Env.Test).from {
    dn: DynamoDocker.Container =>
      dn.availablePorts.get.first(DynamoDocker.primaryPort)
  }

  // and this one is for production
  make[AvailablePort].named("pg").tagged(Env.Prod).from {
    pgPort: Int @Id("postgres.port") =>
      AvailablePort.local(pgPort)
  }

  make[PgSvcExample]

  include(new ConfigModuleDef {
    makeConfigNamed[Int]("postgres.port")
  })
}
