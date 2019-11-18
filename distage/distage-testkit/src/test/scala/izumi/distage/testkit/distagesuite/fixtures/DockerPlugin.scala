package izumi.distage.testkit.distagesuite.fixtures

import izumi.distage.config.annotations.ConfPath
import izumi.distage.model.definition.Id
import izumi.distage.plugins.PluginDef
import izumi.distage.testkit.distagesuite.fixtures
import izumi.distage.testkit.integration.Docker.{ContainerConfig, ContainerHealthCheck, DockerPort}
import izumi.distage.testkit.integration.{ContainerDecl, _}

sealed trait DynamoContainerDecl
object DynamoContainerDecl extends ContainerDecl[DynamoContainerDecl] {
  val primaryPort: DockerPort = DockerPort.TCP(8042)

  override def config: Config = {
    ContainerConfig(
      "amazon/dynamodb-local:latest",
      Seq(primaryPort),
      healthCheck = ContainerHealthCheck.portCheckHead(),
    )
  }
}


sealed trait PgContainerDecl
object PgContainerDecl extends ContainerDecl[PgContainerDecl] {
  val primaryPort: DockerPort = DockerPort.TCP(5432)

  override def config: Config = {
    ContainerConfig(
      "library/postgres:latest",
      Seq(primaryPort),
      healthCheck = ContainerHealthCheck.portCheckHead(),
    )
  }
}

case class ServicePort(number: Int)

class PgSvcExample(val pg: ServicePort@Id("pg"), val ddb: ServicePort@Id("ddb")) {

}

object DockerPlugin extends DockerContainerModule[zio.Task] with PluginDef {
  make[fixtures.DynamoContainerDecl.Type].fromResource(DockerContainerResource[zio.Task].make(DynamoContainerDecl))

  // this container will start once `DynamoContainer` is up and running
  make[PgContainerDecl.Type].fromResource {
    (client: DockerClientWrapper[zio.Task], _: DynamoContainerDecl.Type) =>
      DockerContainerResource.make(PgContainerDecl, client)
  }

  // these lines are for test scope
  make[ServicePort].named("pg").from {pg: PgContainerDecl.Type => ServicePort(pg.mapping(PgContainerDecl.primaryPort))}
  make[ServicePort].named("ddb").from {pg: DynamoContainerDecl.Type => ServicePort(pg.mapping(DynamoContainerDecl.primaryPort))}

  // and this one for production
  // make[ServicePort].named("pg").from { pgPort: Int@ConfPath("postgres.port") => ServicePort(pgPort) }

  make[PgSvcExample]
}
