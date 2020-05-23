package izumi.distage.docker.modules

import com.github.dockerjava.api.command.DockerCmdExecFactory
import distage.TagK
import izumi.distage.config.ConfigModuleDef
import izumi.distage.docker.{Docker, DockerClientWrapper, DockerCmdExecFactoryResource}
import izumi.distage.model.definition.ModuleDef

class DockerSupportModule[F[_]: TagK] extends ModuleDef {
  make[DockerClientWrapper[F]].fromResource[DockerClientWrapper.Resource[F]]
  make[DockerCmdExecFactory].fromResource[DockerCmdExecFactoryResource[F]]

  include(DockerSupportModule.config)
}

object DockerSupportModule {
  def apply[F[_]: TagK]: DockerSupportModule[F] = new DockerContainerModule[F]

  final val config = new ConfigModuleDef {
    makeConfigWithDefault[Docker.ClientConfig]("docker") {
      Docker.ClientConfig(
        readTimeoutMs = 60000,
        connectTimeoutMs = 500,
        allowReuse = true,
        useRemote = false,
        useRegistry = false,
        remote = None,
        registry = None,
      )
    }
  }
}
