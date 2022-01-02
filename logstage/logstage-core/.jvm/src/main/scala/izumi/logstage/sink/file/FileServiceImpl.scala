package izumi.logstage.sink.file

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{FileAlreadyExistsException, Files, Paths, StandardOpenOption}

import izumi.fundamentals.platform.language.Quirks._
import izumi.logstage.sink.file.FileServiceImpl.RealFile
import izumi.logstage.sink.file.models.LogFile

import scala.io.Source
import scala.util.Try

class FileServiceImpl(override val path: String) extends FileService[RealFile] {
  override val createFileWithName: String => RealFile = RealFile.apply
}

object FileServiceImpl {

  case class RealFile(override val name: String) extends LogFile {

    private def filePath = Paths.get(name)

    private def fileSrc = Try {
      Source.fromFile(this.name)
    }

    private val outputSink =
      for {
        _ <- createIfNotExists
        writer <- Try(Files.newOutputStream(filePath, StandardOpenOption.APPEND))
      } yield writer

    override def exists: Boolean = {
      new File(name).exists()
    }

    override def size: Int = {
      fileSrc.map(_.getLines().size).get
    }

    override def getContent: Iterable[String] = {
      fileSrc.map(_.getLines().toList).get
    }

    override def append(item: String): Unit = {
      outputSink.foreach(_.write(s"$item\n".getBytes(StandardCharsets.UTF_8)))
    }

    override def beforeDelete(): Unit = {
      closeStream.foreach {
        _ => Files.deleteIfExists(filePath)
      }
    }

    private def closeStream: Try[Unit] =
      for {
        stream <- outputSink
        _ <- Try(stream.flush())
        _ <- Try(stream.close())
      } yield ()

    private def createIfNotExists: Try[Unit] = {
      Try {
        filePath.getParent.toFile.mkdirs().discard()
        Files.createFile(filePath).discard()
      } recover {
        case _: FileAlreadyExistsException =>
      }
    }
  }

}
