package izumi.distage.testkit.docker.fixtures

import distage.config.ConfPath
import izumi.distage.model.definition.Id
import izumi.distage.model.definition.StandardAxis.Env
import izumi.distage.monadic.modules.{CatsDIEffectModule, ZIODIEffectModule}
import izumi.distage.plugins.PluginDef
import izumi.distage.testkit.integration.docker.Docker.AvailablePort
import izumi.distage.testkit.integration.docker.examples.{DynamoDocker, PostgresDocker}
import izumi.distage.testkit.integration.docker.modules.DockerContainerModule
import zio.Task

class PgSvcExample(val pg: AvailablePort@Id("pg"), val ddb: AvailablePort@Id("ddb"))

object MonadPlugin extends PluginDef
  with CatsDIEffectModule
  with ZIODIEffectModule

object DockerPlugin extends DockerContainerModule[Task] with PluginDef {
  make[DynamoDocker.Container].fromResource {
    DynamoDocker.make[Task]
  }

  // this container will start once `DynamoContainer` is up and running
  make[PostgresDocker.Container].fromResource {
    PostgresDocker.make[Task]
      .dependOnDocker(DynamoDocker)
  }

  // these lines are for test scope
  make[AvailablePort].named("pg").tagged(Env.Test).from {
    pg: PostgresDocker.Container =>
      pg.availablePorts(PostgresDocker.primaryPort).head
  }
  make[AvailablePort].named("ddb").tagged(Env.Test).from {
    dn: DynamoDocker.Container =>
      dn.availablePorts(DynamoDocker.primaryPort).head
  }

  // and this one is for production
  make[AvailablePort].named("pg").tagged(Env.Prod).from {
    pgPort: Int @ConfPath("postgres.port") =>
      AvailablePort.local(pgPort)
  }

  make[PgSvcExample]

}
