package com.github.pshirshov.izumi

import com.github.pshirshov.izumi.FileSink.FileIdentity
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks.Discarder
import com.github.pshirshov.izumi.models.LogFile

import scala.collection.mutable
import scala.util.{Success, Try}

trait FileService[File <: LogFile] {

  val storage: mutable.Map[FileIdentity, File] = scala.collection.mutable.HashMap.empty[FileIdentity, File]

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