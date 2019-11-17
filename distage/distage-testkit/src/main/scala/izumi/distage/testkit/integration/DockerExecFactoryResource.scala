package izumi.distage.testkit.integration

import com.github.dockerjava.api.command.DockerCmdExecFactory
import com.github.dockerjava.netty.NettyDockerCmdExecFactory
import izumi.distage.config.annotations.ConfPath
import izumi.distage.model.definition.DIResource
import izumi.distage.model.monadic.DIEffect

class DockerExecFactoryResource[F[_] : DIEffect](config: Docker.ClientConfig@ConfPath("docker")) extends DIResource[F, DockerCmdExecFactory] {
  override def acquire: F[DockerCmdExecFactory] = {
    DIEffect[F].maybeSuspend {
      new NettyDockerCmdExecFactory()
        .withReadTimeout(config.readTimeoutMs)
        .withConnectTimeout(config.connectTimeoutMs)
    }
  }

  override def release(resource: DockerCmdExecFactory): F[Unit] = DIEffect[F].maybeSuspend(resource.close())
}
