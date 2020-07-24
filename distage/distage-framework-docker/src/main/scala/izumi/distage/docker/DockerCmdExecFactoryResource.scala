package izumi.distage.docker

import com.github.dockerjava.api.command.DockerCmdExecFactory
import com.github.dockerjava.netty.NettyDockerCmdExecFactory
import izumi.distage.model.definition.DIResource
import izumi.distage.model.effect.DIEffect

final class DockerCmdExecFactoryResource[F[_]: DIEffect](
  config: Docker.ClientConfig
) extends DIResource.FromAutoCloseable[F, DockerCmdExecFactory](
    DIEffect[F].maybeSuspend {
      new NettyDockerCmdExecFactory()
        .withReadTimeout(config.readTimeoutMs)
        .withConnectTimeout(config.connectTimeoutMs)
    }
  )
