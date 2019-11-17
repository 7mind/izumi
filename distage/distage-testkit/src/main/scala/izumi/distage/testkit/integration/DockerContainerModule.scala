package izumi.distage.testkit.integration

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.DockerCmdExecFactory
import distage.TagK
import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.monadic.DIEffect
import izumi.distage.roles.model.IntegrationCheck

class DockerContainerModule[F[_] : DIEffect : TagK] extends ModuleDef {
  make[DockerClientWrapper].fromResource[DockerClientResource[F]]
  make[DockerClient].from {
    wrapper: DockerClientWrapper => wrapper.client
  }
  many[IntegrationCheck].ref[DockerClientWrapper]

  make[DockerCmdExecFactory].fromResource[DockerExecFactoryResource[F]]
}
