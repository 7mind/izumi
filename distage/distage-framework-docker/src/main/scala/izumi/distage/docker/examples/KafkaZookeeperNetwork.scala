package izumi.distage.docker.examples

import izumi.distage.docker.ContainerNetworkDef

object KafkaZookeeperNetwork extends ContainerNetworkDef {
  override def config: Config = Config()
}
