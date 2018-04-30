package com.github.pshirshov.izumi.models

import java.nio.file.{FileAlreadyExistsException, Files, Paths, StandardOpenOption}
import scala.io.Source
import scala.util.{Failure, Success, Try}
import LogFile.TryOps._

trait LogFile {
  def name: FileId

  def path: String

  def status: LogFileStatus

  def write(item: String): Try[Unit]

  def updateStatus(status: LogFileStatus): Try[LogFile]

  def getFileSize: Try[Int]

  def remove: Try[Unit]

  protected lazy val filePath: String = LogFile.buildName(path, name)
}

object LogFile {

  case class DummyFile(path: String, name: FileId, status: LogFileStatus = LogFileStatus.FirstEntry) extends LogFile {

    private lazy val state = scala.collection.mutable.ListBuffer.empty[String]

    override def write(item: String): Try[Unit] = {
      state += item
      ()
    }

    override def updateStatus(status: LogFileStatus): Try[LogFile] = {
      this.copy(status = status)
    }

    override def getFileSize: Try[Int] = {
      state.size
    }

    override def remove: Try[Unit] = {
      state.clear()
    }
  }

  case class RealFile(path: String, name: FileId, status: LogFileStatus = LogFileStatus.FirstEntry) extends LogFile {

    private lazy val outputStreamWriter = for {
      _ <- createFile
      res <- Try(Files.newOutputStream(Paths.get(filePath), StandardOpenOption.APPEND))
    } yield res

    private lazy val fileReader = {
      for {
        _ <- createFile
        res <- Try(Source.fromFile(filePath))
      } yield res
    }

    override def write(item: String): Try[Unit] = {
      for {
        stream <- outputStreamWriter
        res <- Try(stream.write(s"$item\n".getBytes())) // UTF-8
      } yield res
    }

    override def updateStatus(status: LogFileStatus): Try[LogFile] = {
      for {
        _ <- closeStream
        withStatus <- Success(this.copy(status = status))
      } yield withStatus
    }

    override def getFileSize: Try[Int] = for {
      reader <- fileReader
      res <- Try(reader.getLines.size)
    } yield res

    override def remove: Try[Unit] = {
      for {
        _ <- closeStream
        res <- Try(Files.deleteIfExists(Paths.get(filePath)))
      } yield res
    }

    private def closeStream: Try[Unit] = {
      for {
        stream <- outputStreamWriter
        _ <- Try(stream.flush())
        _ <- Try(stream.close())
      } yield ()
    }

    def createFile: Try[Unit] = {
      try {
        Files.createFile(Paths.get(filePath))
        Success(())
      } catch {
        case _: FileAlreadyExistsException =>
          Success(())
        case other =>
          Failure(other)
      }
    }
  }

  def buildName(path: String, fileId: FileId): String = {
    s"$path/log.$fileId.txt"
  }

  object TryOps {
    implicit def asSuccess[T](t: T): Try[T] = {
      Success(t)
    }
  }

}
