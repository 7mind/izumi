package com.github.pshirshov.izumi

import java.io.{File, OutputStream}
import java.nio.file.{Files, Paths, StandardOpenOption}

import com.github.pshirshov.izumi.FileSink.FileIdentity

import scala.util.{Success, Try}
import scala.collection.concurrent
import java.util.concurrent.ConcurrentHashMap

import com.github.pshirshov.izumi.FileServiceImpl.LogFile
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks

import collection.JavaConverters._
import scala.io.Source
class FileServiceImpl(override val path : String) extends FileService {

  val storage: concurrent.Map[FileIdentity, LogFile] = new ConcurrentHashMap[FileIdentity, LogFile].asScala


  override def getFileIds: Try[Set[FileIdentity]] = {
    Success {
      storage.keySet.toSet
    }
  }

  override def fileContent(fileIdentity: FileIdentity): Try[List[String]] = {
    for {
      file <- Try(storage(fileIdentity))
      res <- Try {
        Source.fromFile(file.name)
      }.map(_.getLines.toList)
    } yield res
  }

  override def fileSize(fileIdentity: FileIdentity): Try[Int] = {
    fileContent(fileIdentity).map(_.size)
  }

  override def clearFile(fileIdentity: FileIdentity): Try[Unit] = ???
//  {
//    removeFile(fileIdentity).map(writeToFile())
//  }

  override def removeFile(fileIdentity: FileIdentity): Try[Unit] = {
    for {
      file <- Try(storage(fileIdentity))
      _ <- Try(Files.deleteIfExists(Paths.get(file.name)))
    } yield {
      Quirks.discard(storage.remove(fileIdentity))
    }
  }




//  res <- Try(Files.newOutputStream(Paths.get(filePath), StandardOpenOption.APPEND))

  override def writeToFile(fileIdentity: FileIdentity, content: String): Try[Unit] = ???

  override protected def provideFileName(fileIdentity: FileIdentity): String = {
    s"$path/log$fileIdentity.txt"
  }
}

object FileServiceImpl {
  case class LogFile(name : String, file : File, writer : OutputStream)
}