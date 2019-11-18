package izumi.distage.testkit.integration

import com.github.dockerjava.api.command.DockerCmdExecFactory
import distage.TagK
import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.monadic.DIEffect
import izumi.distage.roles.model.IntegrationCheck

class DockerContainerModule[F[_] : DIEffect : TagK] extends ModuleDef {
  make[DockerClientWrapper[F]].fromResource[DockerClientResource[F]]
  many[IntegrationCheck].ref[DockerClientWrapper[F]]
  make[DockerCmdExecFactory].fromResource[DockerExecFactoryResource[F]]
}
