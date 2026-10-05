package izumi.distage.docker.model

import com.github.dockerjava.api.DockerClient
import izumi.distage.docker.model.Docker.ContainerId

/**
  * Runs against a container after it is created and before it is started.
  *
  * A container is reused only if it was created with hooks whose [[reuseKey]]s are identical, in the same order.
  * The key must therefore identify everything the hook changes in the container, and must be stable across runs
  * for reuse to work at all.
  *
  * If [[afterCreate]] throws, the container is removed and acquisition fails with a [[DockerFailureException]]
  * whose cause is the hook's exception.
  */
trait ContainerHook {
  def reuseKey: String
  def afterCreate(client: DockerClient, containerId: ContainerId): Unit
}
