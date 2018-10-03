package com.github.pshirshov.izumi.logstage.sink.file

import java.util.concurrent.ConcurrentHashMap

import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks.Discarder
import com.github.pshirshov.izumi.logstage.sink.file.FileSink.FileIdentity
import com.github.pshirshov.izumi.logstage.sink.file.models.LogFile

import scala.collection.JavaConverters._
import scala.collection.concurrent
import scala.util.Try

trait FileService[File <: LogFile] {

  val storage: concurrent.Map[FileIdentity, File] = new ConcurrentHashMap[FileIdentity, File]().asScala

  def path: String

  def createFileWithName: String => File

  def createFile: FileIdentity => File = {
    id =>
      createFileWithName(FileService.provideFileName(path, id))
  }

  def exists(fileIdentity: FileIdentity): Boolean = {
    storage.get(fileIdentity).exists(_.exists)
  }

  def scanDirectory: Set[FileIdentity] = {
    storage.collect {
      case (id, file) if file.exists => id
    }.toSet
  }

  def fileContent(fileIdentity: FileIdentity): Option[Iterable[String]] = {
    storage.get(fileIdentity).map(_.getContent)
  }

  def fileSize(fileIdentity: FileIdentity): Int = {
    storage.get(fileIdentity).map(_.size).get
  }

  def removeFile(fileIdentity: FileIdentity): Unit = {
    storage.get(fileIdentity).foreach {
      f =>
        f.beforeDelete()
        storage.remove(fileIdentity).discard()
    }
  }

  def writeToFile(fileIdentity: FileIdentity, content: String): Try[Unit] = {
    for {
      file <- Try(storage.getOrElseUpdate(fileIdentity, createFile(fileIdentity)))
      _ <- Try(file.append(content))
    } yield storage.put(fileIdentity, file).discard()
  }
}

object FileService {
  def provideFileName(path: String, fileIdentity: FileIdentity): String = {
    s"$path/log.$fileIdentity.txt"
  }
}
