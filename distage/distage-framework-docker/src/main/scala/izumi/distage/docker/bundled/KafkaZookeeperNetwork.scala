package izumi.distage.docker.bundled

import izumi.distage.docker.ContainerNetworkDef

object KafkaZookeeperNetwork extends ContainerNetworkDef {
  override def config: Config = Config()
}
