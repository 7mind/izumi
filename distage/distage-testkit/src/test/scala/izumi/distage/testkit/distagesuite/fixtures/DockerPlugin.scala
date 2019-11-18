package izumi.distage.testkit.distagesuite.fixtures

import izumi.distage.model.monadic.{DIEffect, DIEffectAsync}
import izumi.distage.plugins.PluginDef
import izumi.distage.testkit.distagesuite.fixtures
import izumi.distage.testkit.integration.Docker.{ContainerConfig, ContainerHealthCheck, DockerPort}
import izumi.distage.testkit.integration.{ContainerDecl, _}
import izumi.logstage.api.IzLogger

sealed trait DynamoContainerDecl
object DynamoContainerDecl extends ContainerDecl[DynamoContainerDecl] {
  override def config: Config = ContainerConfig(
    "amazon/dynamodb-local:latest",
    Seq(DockerPort.TCP(8042)),
    healthCheck = ContainerHealthCheck.portCheckHead(),
  )
}


sealed trait PgContainerDecl
object PgContainerDecl extends ContainerDecl[PgContainerDecl] {
  override def config: Config = ContainerConfig(
    "library/postgres:latest",
    Seq(DockerPort.TCP(5432)),
    healthCheck = ContainerHealthCheck.portCheckHead(),
  )
}

class PgSvcExample(val pg: PgContainerDecl.Type) {

}

object DockerPlugin extends DockerContainerModule[zio.Task] with PluginDef {
  make[fixtures.DynamoContainerDecl.Type].fromResource(DockerContainerResource[zio.Task].make(DynamoContainerDecl))
  make[PgContainerDecl.Type].fromResource {
    (client: DockerClientWrapper[zio.Task], _: DynamoContainerDecl.Type) =>
      DockerContainerResource.make(PgContainerDecl, client)
  }

  make[PgSvcExample]
}
