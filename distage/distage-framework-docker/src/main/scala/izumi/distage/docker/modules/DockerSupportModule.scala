package izumi.distage.docker.modules

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.core.DefaultDockerClientConfig
import distage.{ModuleBase, TagK}
import izumi.distage.config.ConfigModuleDef
import izumi.distage.docker.impl.DockerClientWrapper.DockerIntegrationCheck
import izumi.distage.docker.impl.{DockerClientFactory, DockerClientWrapper}
import izumi.distage.docker.model.Docker
import izumi.distage.model.definition.{Lifecycle, ModuleDef}
import izumi.functional.Value

class DockerSupportModule[F[_]: TagK](configModule: ModuleBase) extends ModuleDef {
  include(configModule)

  make[DockerClientWrapper[F]].fromResource[DockerClientWrapper.Resource[F]]
  make[DockerClientFactory].from(DockerClientFactory.impl)

  make[DefaultDockerClientConfig].from {
    (clientConfig: Docker.ClientConfig) =>
      /** We do not need to use global registry here since it would be overridden in CMD requests. */
      val remote = clientConfig.remote.filter(_ => clientConfig.useRemote)
      Value(DefaultDockerClientConfig.createDefaultConfigBuilder())
        .mut(remote)((b, c) => b.withDockerHost(c.host).withDockerTlsVerify(c.tlsVerify).withDockerCertPath(c.certPath).withDockerConfig(c.config))
        .get.build()
  }

  make[DockerClient].fromResource {
    (clientConfig: Docker.ClientConfig, rawClientConfig: DefaultDockerClientConfig, clientFactory: DockerClientFactory) =>
      Lifecycle.fromAutoCloseable(clientFactory.makeClient(clientConfig, rawClientConfig))
  }

  make[DockerIntegrationCheck[F]]
}

object DockerSupportModule {
  def apply[F[_]: TagK]: ModuleBase = new DockerSupportModule[F](Configs.config)

  def default[F[_]: TagK]: ModuleBase = new DockerSupportModule[F](Configs.defaultConfig)

  object Configs {
    final val config = new ConfigModuleDef {
      makeConfigWithDefault[Docker.ClientConfig]("docker")(Docker.ClientConfig())
    }

    final val defaultConfig = new ConfigModuleDef {
      make[Docker.ClientConfig].fromValue(Docker.ClientConfig())
    }
  }
}
