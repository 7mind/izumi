package izumi.distage.docker

import izumi.distage.docker.impl.CopyFilesHook
import izumi.distage.docker.model.ContainerHook
import izumi.distage.docker.model.Docker.ContainerFile

object ContainerHooks {

  /**
    * Copies files and directories this JVM reads into the container, so the container engine never needs access to the
    * host path. Copies are owned by root, world-readable, and executable only when the source file is; modification
    * times are not preserved. The reuse key is a digest of the copied content, container paths and executable bits.
    */
  def copyFiles(files: ContainerFile*): ContainerHook = new CopyFilesHook(files.toList)
}
