package izumi.distage.docker.impl

import izumi.distage.docker.model.Docker.ContainerFile
import izumi.distage.docker.model.{DockerFailureCause, DockerFailureException}
import izumi.fundamentals.platform.bytes.IzBytes.*
import izumi.fundamentals.platform.language.Quirks.Discarder
import org.apache.commons.compress.archivers.tar.{TarArchiveEntry, TarArchiveOutputStream}

import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.attribute.FileTime
import java.nio.file.{FileVisitOption, Files, Path, Paths}
import java.security.{DigestOutputStream, MessageDigest}
import java.time.Instant
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

final class StagedArchives(val archives: List[Path], val digest: String) extends AutoCloseable {
  override def close(): Unit = archives.foreach(Files.deleteIfExists(_).discard())
}

object ContainerFileArchive {
  final val extractionRoot = "/"

  private final val digestAlgorithm = "SHA-256"
  private final val tempDirectoryProperty = "java.io.tmpdir"
  private final val stagingPrefix = "distage-container-files"
  private final val stagingSuffix = ".tar"
  private final val separator = "/"
  private final val currentDirectory = "."
  private final val parentDirectory = ".."
  private final val rootId = 0
  private final val rootName = "root"
  private final val executePermissions = Integer.parseInt("111", 8)
  private final val modificationTime = FileTime.from(Instant.EPOCH)

  def digest(files: Seq[ContainerFile]): String = {
    val sha = MessageDigest.getInstance(digestAlgorithm)
    files.foreach(file => write(file, new DigestOutputStream(OutputStream.nullOutputStream(), sha)))
    sha.digest().toHex
  }

  def stage(files: Seq[ContainerFile]): StagedArchives = {
    stage(files, Paths.get(System.getProperty(tempDirectoryProperty)))
  }

  def stage(files: Seq[ContainerFile], directory: Path): StagedArchives = {
    val sha = MessageDigest.getInstance(digestAlgorithm)
    val staged = List.newBuilder[Path]
    try {
      files.foreach {
        file =>
          val target = Files.createTempFile(directory, stagingPrefix, stagingSuffix)
          staged += target
          write(file, new DigestOutputStream(Files.newOutputStream(target), sha))
      }
      new StagedArchives(staged.result(), sha.digest().toHex)
    } catch {
      case NonFatal(failure) =>
        try {
          staged.result().foreach(Files.deleteIfExists(_).discard())
        } catch {
          case NonFatal(cleanup) => failure.addSuppressed(cleanup)
        }
        throw failure
    }
  }

  private def write(file: ContainerFile, out: OutputStream): Unit = {
    try {
      writeArchive(file, out)
    } catch {
      case NonFatal(t) =>
        throw DockerFailureException(
          s"Cannot copy `${file.hostPath}` to `${file.containerPath}` in the container: $t",
          DockerFailureCause.Throwed(t),
          t,
        )
    }
  }

  private def writeArchive(file: ContainerFile, out: OutputStream): Unit = {
    val tar = new TarArchiveOutputStream(out, StandardCharsets.UTF_8.name())
    try {
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
