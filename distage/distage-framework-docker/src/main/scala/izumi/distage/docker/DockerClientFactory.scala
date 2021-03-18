package izumi.distage.docker

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.core.{DockerClientBuilder, DockerClientConfig}
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient

trait DockerClientFactory {
  def makeClient(clientConfig: Docker.ClientConfig, rawClientConfig: DockerClientConfig): DockerClient
}

object DockerClientFactory {
  def impl: DockerClientFactory = {
    (_, rawClientConfig) =>
      DockerClientBuilder
        .getInstance(rawClientConfig)
        .withDockerHttpClient(
          new ZerodepDockerHttpClient.Builder()
            .dockerHost(rawClientConfig.getDockerHost)
            .sslConfig(rawClientConfig.getSSLConfig)
            .build()
        )
        .build()
  }
}
