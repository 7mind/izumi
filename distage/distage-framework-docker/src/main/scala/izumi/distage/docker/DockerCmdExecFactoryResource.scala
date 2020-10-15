package izumi.distage.docker

import com.github.dockerjava.api.command.DockerCmdExecFactory
import com.github.dockerjava.netty.NettyDockerCmdExecFactory
import izumi.distage.model.definition.Lifecycle
import izumi.distage.model.effect.QuasiEffect

class DockerCmdExecFactoryResource[F[_]: QuasiEffect](
  config: Docker.ClientConfig
) extends Lifecycle.FromAutoCloseable[F, DockerCmdExecFactory](
    QuasiEffect[F].maybeSuspend {
      new NettyDockerCmdExecFactory()
        .withReadTimeout(config.readTimeoutMs)
        .withConnectTimeout(config.connectTimeoutMs)
    }
  )
