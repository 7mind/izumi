package izumi.distage.testkit.integration

import com.github.dockerjava.api.command.DockerCmdExecFactory
import com.github.dockerjava.core.{DefaultDockerClientConfig, DockerClientBuilder}
import izumi.distage.config.annotations.ConfPath
import izumi.distage.model.definition.DIResource
import izumi.distage.model.monadic.DIEffect

class DockerClientResource[F[_] : DIEffect](config: Docker.ClientConfig@ConfPath("docker"), factory: DockerCmdExecFactory) extends DIResource[F, DockerClientWrapper] {
  override def acquire: F[DockerClientWrapper] = {

    val dcc = DefaultDockerClientConfig.createDefaultConfigBuilder().build()
    //        .withDockerHost("tcp://docker.somewhere.tld:2376")
    //        .withDockerTlsVerify(true)
    //        .withDockerCertPath("/home/user/.docker")
    //        .withRegistryUsername(registryUser)
    //        .withRegistryPassword(registryPass)
    //        .withRegistryEmail(registryMail)
    //        .withRegistryUrl(registryUrl)
    //        .build


    val client = DockerClientBuilder
      .getInstance(dcc)
      .withDockerCmdExecFactory(factory)
      .build

    DIEffect[F].maybeSuspend(new DockerClientWrapper(client))
  }

  override def release(resource: DockerClientWrapper): F[Unit] = DIEffect[F].maybeSuspend(resource.client.close())
}
