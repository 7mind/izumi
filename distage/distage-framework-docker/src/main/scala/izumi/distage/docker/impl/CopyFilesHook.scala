package izumi.distage.docker.impl

import com.github.dockerjava.api.DockerClient
import izumi.distage.docker.model.ContainerHook
import izumi.distage.docker.model.Docker.ContainerFile

import java.io.ByteArrayInputStream

final class CopyFilesHook(files: List[ContainerFile]) extends ContainerHook {
  private lazy val archives: List[ContainerFileArchive] = files.map(ContainerFileArchive.make)

  override lazy val reuseKey: String = s"copy-files:${ContainerFileArchive.digest(archives).getOrElse("")}"

  override def afterCreate(client: DockerClient, containerId: String): Unit = {
    archives.foreach {
      archive =>
        client
          .copyArchiveToContainerCmd(containerId)
          .withRemotePath(ContainerFileArchive.extractionRoot)
          .withTarInputStream(new ByteArrayInputStream(archive.archive))
          .exec()
    }
  }

  override def toString: String = s"CopyFilesHook(${files.map(f => s"${f.hostPath} -> ${f.containerPath}").mkString(", ")})"
}
