package com.github.pshirshov.izumi

import com.github.pshirshov.izumi.FileSink.FileIdentity
import com.github.pshirshov.izumi.models.LogFile

import scala.collection.mutable
import scala.util.{Failure, Success, Try}
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks.Discarder

trait FileService[File <: LogFile] {

  val storage: mutable.Map[FileIdentity, File] = scala.collection.mutable.HashMap.empty[FileIdentity, File]

  def path: String

  def createFileWithName: String => File

  def createFile: FileIdentity => File = {
    id =>
      createFileWithName(FileService.provideFileName(path, id))
  }

  def exists(fileIdentity: FileIdentity) : Try[Boolean] = {
    for {
      file <- Try(storage(fileIdentity))
    } yield file.exists
  }

  def scanDirectory: Set[FileIdentity] = {
    storage.collect {
      case (id, file) if file.exists => id
    }.toSet
  }

  def fileContent(fileIdentity: FileIdentity): Try[Iterable[String]] = {
    for {
      file <- Try(storage(fileIdentity))
    } yield file.getContent
  }

  def fileSize(fileIdentity: FileIdentity): Try[Int] = {
    for {
      file <- Try(storage(fileIdentity))
    } yield file.size
  }

  def removeFile(fileIdentity: FileIdentity): Try[Unit] = {
    for {
      file <- Try(storage(fileIdentity))
      _ <- Success(file.beforeDelete())
    } yield storage.remove(fileIdentity).discard()
  }

  def writeToFile(fileIdentity: FileIdentity, content: String): Try[Unit] = {
    for {
      file <- Try(storage.getOrElseUpdate(fileIdentity, createFile(fileIdentity)))
      _ <- Try(file.append(content))
    } yield storage.put(fileIdentity, file)
  }
}

object FileService {
  def provideFileName(path: String, fileIdentity: FileIdentity): String = {
    s"$path/log.$fileIdentity.txt"
  }
}