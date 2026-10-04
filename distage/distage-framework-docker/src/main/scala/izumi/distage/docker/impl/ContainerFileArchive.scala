package izumi.distage.docker.impl

import izumi.distage.docker.model.Docker.ContainerFile
import izumi.distage.docker.model.{DockerFailureCause, DockerFailureException}
import izumi.fundamentals.platform.bytes.IzBytes.*
import izumi.fundamentals.platform.crypto.IzHash
import izumi.fundamentals.platform.language.Quirks.Discarder
import org.apache.commons.compress.archivers.tar.{TarArchiveEntry, TarArchiveOutputStream}

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.attribute.FileTime
import java.nio.file.{FileVisitOption, Files}
import java.time.Instant
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

final case class ContainerFileArchive(file: ContainerFile, archive: Array[Byte])

object ContainerFileArchive {
  final val extractionRoot = "/"

  private final val separator = "/"
  private final val currentDirectory = "."
  private final val parentDirectory = ".."
  private final val rootId = 0
  private final val rootName = "root"
  private final val executePermissions = Integer.parseInt("111", 8)
  private final val modificationTime = FileTime.from(Instant.EPOCH)

  def make(file: ContainerFile): ContainerFileArchive = {
    try {
      ContainerFileArchive(file, archive(file))
    } catch {
      case NonFatal(t) =>
        throw DockerFailureException(
          s"Cannot copy `${file.hostPath}` to `${file.containerPath}` in the container: $t",
          DockerFailureCause.Throwed(t),
          t,
        )
    }
  }

  def digest(archives: Seq[ContainerFileArchive]): Option[String] = {
    if (archives.isEmpty) {
      None
    } else {
      val all = new ByteArrayOutputStream()
      archives.foreach(a => all.write(a.archive))
      Some(IzHash.sha256(all.toByteArray).toHex)
    }
  }

  private def archive(file: ContainerFile): Array[Byte] = {
    val targetSegments = file.containerPath.split(separator).toList.filter(_.nonEmpty)
    require(file.hostPath.toString.nonEmpty, "the host path is empty")
    require(file.containerPath.startsWith(separator), "the container path is not absolute")
    require(targetSegments.nonEmpty, "the container path is the root directory")
    require(!targetSegments.exists(s => s == currentDirectory || s == parentDirectory), "the container path is not normalized")

    val sources = {
      val stream = Files.walk(file.hostPath, FileVisitOption.FOLLOW_LINKS)
      try stream.iterator().asScala.toList
      finally stream.close()
    }
    val entries = sources
      .map {
        source =>
          val relative = file.hostPath.relativize(source).iterator().asScala.map(_.toString).filter(_.nonEmpty).toList
          ((targetSegments ++ relative).mkString(separator), source)
      }.sortBy(_._1)

    val out = new ByteArrayOutputStream()
    val tar = new TarArchiveOutputStream(out, StandardCharsets.UTF_8.name())
    try {
      tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
      tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
      entries.foreach {
        case (name, source) if Files.isDirectory(source) =>
          tar.putArchiveEntry(entry(name + separator, TarArchiveEntry.DEFAULT_DIR_MODE, 0L))
          tar.closeArchiveEntry()
        case (name, source) if Files.isRegularFile(source) =>
          val mode = if (Files.isExecutable(source)) TarArchiveEntry.DEFAULT_FILE_MODE | executePermissions else TarArchiveEntry.DEFAULT_FILE_MODE
          tar.putArchiveEntry(entry(name, mode, Files.size(source)))
          Files.copy(source, tar).discard()
          tar.closeArchiveEntry()
        case (_, source) =>
          throw new IllegalArgumentException(s"`$source` is neither a regular file nor a directory")
      }
      tar.finish()
    } finally {
      tar.close()
    }
    out.toByteArray
  }

  private def entry(name: String, mode: Int, size: Long): TarArchiveEntry = {
    val entry = new TarArchiveEntry(name)
    entry.setMode(mode)
    entry.setSize(size)
    entry.setIds(rootId, rootId)
    entry.setNames(rootName, rootName)
    entry.setModTime(modificationTime)
    entry
  }
}
