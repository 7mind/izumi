package com.github.pshirshov.izumi

import com.github.pshirshov.izumi.FileSink.FileIdentity

import scala.util.{Success, Try}

class DummyFileService(override val path : String) extends FileService {
  val storage = scala.collection.mutable.HashMap.empty[FileIdentity, DummyFile]

  override def getFileIds: Try[Set[FileIdentity]] = Try(storage.keySet.toSet)

  override def fileContent(fileIdentity: FileIdentity): Try[List[String]] = {
    Try {
      storage(fileIdentity)
    }.map(_.content)
  }

  override def fileSize(fileIdentity: FileIdentity): Try[FileIdentity] = {
    Try {
      storage(fileIdentity)
    }.map(_.size)
  }

  override def clearFile(fileIdentity: FileIdentity): Try[Unit] = {
    Try {
      storage(fileIdentity)
    }.map(_.clear)
  }

  override def removeFile(fileIdentity: FileIdentity): Try[Unit] = {
    Success(storage.remove(fileIdentity))
  }

  override def writeToFile(fileIdentity: FileIdentity, content: String): Try[Unit] = {
    for {
      file <- Try(storage.getOrElseUpdate(fileIdentity, DummyFile(provideFileName(fileIdentity))))
      _ <- Success{
        file.append(content)
      }
      _ <- Success {
        storage.put(fileIdentity, file)
      }
    } yield ()
  }

  override def provideFileName(fileIdentity: FileIdentity): String = {
    fileIdentity.toString
  }
}
