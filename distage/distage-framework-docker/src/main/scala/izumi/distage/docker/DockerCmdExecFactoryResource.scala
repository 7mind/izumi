package izumi.distage.docker

import com.github.dockerjava.api.command.DockerCmdExecFactory
import com.github.dockerjava.netty.NettyDockerCmdExecFactory
import izumi.distage.model.definition.Lifecycle
import izumi.distage.model.effect.DIEffect

class DockerCmdExecFactoryResource[F[_]: DIEffect](
  config: Docker.ClientConfig
) extends Lifecycle.FromAutoCloseable[F, DockerCmdExecFactory](
    DIEffect[F].maybeSuspend {
      new NettyDockerCmdExecFactory()
        .withReadTimeout(config.readTimeoutMs)
        .withConnectTimeout(config.connectTimeoutMs)
    }
  )
