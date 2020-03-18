package izumi.distage.docker.examples

import izumi.distage.docker.ContainerNetworkDef
import izumi.distage.docker.ContainerNetworkDef.ContainerNetworkConfig

object KafkaZookeeperNetwork extends ContainerNetworkDef {
  override def config: KafkaZookeeperNetwork.Config = ContainerNetworkConfig(
    reuse = false
  )
}
