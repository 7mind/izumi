package com.github.pshirshov.izumi

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{FileAlreadyExistsException, Files, Paths, StandardOpenOption}

import scala.util.{Failure, Success, Try}
import com.github.pshirshov.izumi.FileServiceImpl.RealFile
import com.github.pshirshov.izumi.models.LogFile

import scala.io.Source

class FileServiceImpl(override val path: String) extends FileService[RealFile] {
  override val createFileWithName: String => RealFile = RealFile.apply
}

object FileServiceImpl {

  case class RealFile(override val name : String) extends LogFile {

    private def filePath = Paths.get(name)

    private def fileSrc = Try {
      Source.fromFile(this.name)
    }

    override def exists: Boolean = {
      new File(name).exists()
    }

    private val fileWriter = {
      for {
        _ <- createIfNotExists
        writer <- Try(Files.newOutputStream(filePath, StandardOpenOption.APPEND))
      } yield writer

    }

    override def size: Try[Int] = {
      for {
        src <- fileSrc
        size <- Try(src.getLines().size)
      } yield size
    }

    override def getContent: Try[Iterable[String]] = {
      for {
        src <- fileSrc
        lines <- Try(src.getLines())
      } yield lines.toList
    }

    override def append(item: String): Try[Unit] = {
      for {
        writer <- fileWriter
        _ <- Try(writer.write(s"$item\n".getBytes(StandardCharsets.UTF_8)))
      } yield ()
    }

    override def beforeDelete(): Try[Unit] = {
      for {
        writer <- fileWriter
        _ <- Try(writer.flush())
        _ <- Try(writer.close())
        _ <- Try(Files.deleteIfExists(filePath))
      } yield ()
    }

    def createIfNotExists: Try[Unit] = {
      (Try {
        Files.createFile(filePath)
      } recover {
        case _: FileAlreadyExistsException =>
          ()
      }).map(_ => ())
    }
  }
}