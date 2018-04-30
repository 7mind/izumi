package com.github.pshirshov.izumi.algebra

import java.nio.file.{FileAlreadyExistsException, Files, Paths, StandardOpenOption}
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks

import scala.io.Source
import scala.util.{Failure, Success, Try}

trait LogFile {
  def name: String

  def path: String

  def status: LogFileStatus

  def writeFile(item: String): Try[Unit]

  def updateStatus(status: LogFileStatus): Try[LogFile]

  def readFile: Try[Seq[String]]

  def remove: Try[Unit]

  protected lazy val filePath: String = LogFile.buildName(path, name)
}

object LogFile {

  case class DummyFile(status: LogFileStatus = LogFileStatus.Pending) extends LogFile {

    override def name: String = ""

    override def path: String = ""

    private lazy val state = scala.collection.mutable.ListBuffer.empty[String]

    override def writeFile(item: String): Try[Unit] = {
      Success {
        state += item
      }
    }

    override def updateStatus(status: LogFileStatus): Try[LogFile] = {
      Success {
        this.copy(status)
      }
    }

    override def readFile: Try[Seq[String]] = Success {
      state.toList
    }

    override def remove: Try[Unit] = Success {
      state.clear()
    }
  }

  case class RealFile(path: String, name: String, status: LogFileStatus = LogFileStatus.Pending) extends LogFile {

    private lazy val outputStreamWriter = for {
      _ <- createFile
      res <- Try(Files.newOutputStream(Paths.get(filePath), StandardOpenOption.APPEND))
    } yield res

    private lazy val fileReader = Try {
      Source.fromFile(filePath)
    }

    override def writeFile(item: String): Try[Unit] = {
      for {
        stream <- outputStreamWriter
        res <- Try(stream.write(item.getBytes())) // UTF-8
      } yield res
    }

    override def updateStatus(status: LogFileStatus): Try[LogFile] = {
      for {
        _ <- closeStream
        withStatus <- Success(this.copy(status = status))
      } yield withStatus
    }

    override def readFile: Try[Seq[String]] = for {
      reader <- fileReader
      res <- Try {
        reader.getLines.toList
      }
    } yield res

    override def remove: Try[Unit] = {
      for {
        a <- closeStream
        res <- Try {
          Files.deleteIfExists(Paths.get(filePath))
        }
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
        val res = Files.createFile(Paths.get(filePath))
        Success(Quirks.discard(res))
      } catch {
        case _: FileAlreadyExistsException =>
          Success(Quirks.discard())
        case other =>
          Failure(other)
      }
    }
  }


  def buildName(path: String, name: String): String = {
    s"$path/log.$name.txt"
  }
}
