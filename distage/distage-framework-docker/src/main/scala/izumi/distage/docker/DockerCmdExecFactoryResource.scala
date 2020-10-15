package izumi.distage.docker

import com.github.dockerjava.api.command.DockerCmdExecFactory
import com.github.dockerjava.netty.NettyDockerCmdExecFactory
import izumi.distage.model.definition.Lifecycle
import izumi.distage.model.effect.QuasiIO

class DockerCmdExecFactoryResource[F[_]: QuasiIO](
  config: Docker.ClientConfig
) extends Lifecycle.FromAutoCloseable[F, DockerCmdExecFactory](
    QuasiIO[F].maybeSuspend {
      new NettyDockerCmdExecFactory()
        .withReadTimeout(config.readTimeoutMs)
        .withConnectTimeout(config.connectTimeoutMs)
    }
  )
