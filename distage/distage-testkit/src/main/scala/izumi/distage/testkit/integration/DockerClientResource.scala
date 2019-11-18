package izumi.distage.testkit.integration

import java.util.UUID

import com.github.dockerjava.api.command.DockerCmdExecFactory
import com.github.dockerjava.core.{DefaultDockerClientConfig, DockerClientBuilder}
import izumi.distage.config.annotations.ConfPath
import izumi.distage.model.definition.DIResource
import izumi.distage.model.monadic.DIEffect
import DIEffect.syntax._
import izumi.distage.testkit.integration.Docker.ContainerId

import scala.jdk.CollectionConverters._


class DockerClientResource[F[_] : DIEffect](config: Docker.ClientConfig@ConfPath("docker"), factory: DockerCmdExecFactory) extends DIResource[F, DockerClientWrapper[F]] {
  override def acquire: F[DockerClientWrapper[F]] = {

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

    DIEffect[F].maybeSuspend(new DockerClientWrapper[F](client, Map("type" -> "distage-testkit", "distage-run" -> UUID.randomUUID().toString)))
  }

  override def release(resource: DockerClientWrapper[F]): F[Unit] = {
    for {
      containers <- DIEffect[F].maybeSuspend(resource.client.listContainersCmd().withLabelFilter(resource.labels.asJava).exec())
      _ <- DIEffect[F].traverse_(containers.asScala)(c => resource.destroyContainer(ContainerId(c.getId)))
      _ <- DIEffect[F].maybeSuspend(resource.client.close())
    } yield {

    }
  }
}
