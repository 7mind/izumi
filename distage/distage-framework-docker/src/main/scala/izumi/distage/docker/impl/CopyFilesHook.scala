package izumi.distage.docker.impl

import com.github.dockerjava.api.DockerClient
import izumi.distage.docker.model.ContainerHook
import izumi.distage.docker.model.Docker.{ContainerFile, ContainerId}

import java.nio.file.Files
import scala.util.Using

final class CopyFilesHook(files: List[ContainerFile]) extends ContainerHook {
  private lazy val digest: String = ContainerFileArchive.digest(files)

  override lazy val reuseKey: String = s"copy-files:$digest"

  override def afterCreate(client: DockerClient, containerId: ContainerId): Unit = {
    Using.resource(ContainerFileArchive.stage(files)) {
      staged =>
        if (staged.digest != digest) {
          throw new IllegalStateException(s"The files of $this changed after the container's reuse digest was computed")
        }
        staged.archives.foreach {
          archive =>
            Using.resource(Files.newInputStream(archive)) {
              in =>
                client
                  .copyArchiveToContainerCmd(containerId.name)
                  .withRemotePath(ContainerFileArchive.extractionRoot)
                  .withTarInputStream(in)
                  .exec()
            }
        }
    }
  }

  override def toString: String = s"CopyFilesHook(${files.map(f => s"${f.hostPath} -> ${f.containerPath}").mkString(", ")})"
}
