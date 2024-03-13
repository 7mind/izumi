package izumi.distage.docker.impl

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.core.{DockerClientConfig, DockerClientImpl}
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient
import izumi.distage.docker.model.Docker

trait DockerClientFactory {
  def makeClient(clientConfig: Docker.ClientConfig, rawClientConfig: DockerClientConfig): DockerClient
}

object DockerClientFactory {
  def impl: DockerClientFactory = {
    (_, rawClientConfig) =>
      DockerClientImpl.getInstance(
        rawClientConfig,
        new ZerodepDockerHttpClient.Builder()
          .dockerHost(rawClientConfig.getDockerHost)
          .sslConfig(rawClientConfig.getSSLConfig)
          .build(),
      )
  }
}
