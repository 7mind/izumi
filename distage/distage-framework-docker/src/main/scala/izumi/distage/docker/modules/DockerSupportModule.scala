package izumi.distage.docker.modules

import com.github.dockerjava.api.command.DockerCmdExecFactory
import distage.TagK
import izumi.distage.config.ConfigModuleDef
import izumi.distage.model.definition.ModuleDef
import izumi.distage.docker.{Docker, DockerClientWrapper, DockerCmdExecFactoryResource}

class DockerSupportModule[F[_]: TagK] extends ModuleDef {
  make[DockerClientWrapper[F]].fromResource[DockerClientWrapper.Resource[F]]
  make[DockerCmdExecFactory].fromResource[DockerCmdExecFactoryResource[F]]

  include(DockerSupportModule.config)
}

object DockerSupportModule {
  def apply[F[_]: TagK]: DockerSupportModule[F] = new DockerContainerModule[F]

  final val config = new ConfigModuleDef {
    make[Docker.ClientConfig].from(wireConfigWithDefault("docker") {
      Docker.ClientConfig(
        readTimeoutMs = 60000,
        connectTimeoutMs = 500,
        allowReuse = true,
        useRemote = false,
        useRegistry = false,
        remote = None,
        registry = None,
      )
    })
  }
}
