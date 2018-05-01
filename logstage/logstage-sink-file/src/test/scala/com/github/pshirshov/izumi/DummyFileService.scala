package com.github.pshirshov.izumi

import com.github.pshirshov.izumi.FileSink.FileIdentity

import scala.util.{Success, Try}

class DummyFileService extends FileService {
  val storage = scala.collection.mutable.HashMap.empty[String, Set[DummyFile]]

  override def getFileIdsIn(path: String): Try[Set[String]] = {
    Success {
      storage.getOrElseUpdate(path, Set.empty).map(_.name)
    }
  }

  override def getFileId(fullName: String): Try[Int] = {
    for {
      (_, name) <- FileService.parseFileName(fullName)
      name <- Try(name.takeWhile(_.isDigit).toInt)
    } yield name
  }

  override def fileContent(fileName: String): Try[List[String]] = {
    for {
      (path, _) <- FileService.parseFileName(fileName)
    } yield {
      storage.getOrElseUpdate(path, Set.empty).filter(_.name == fileName).map(_.content).head
    }
  }

  override def fileSize(fileName: String): Try[FileIdentity] = {
    for {
      (path, _) <- FileService.parseFileName(fileName)
    } yield {
      storage.getOrElseUpdate(path, Set.empty).filter(_.name == fileName).map(_.size).head
    }
  }

  override def clearFile(fileName: String): Try[Unit] = {
    for {
      (path, _) <- FileService.parseFileName(fileName)
    } yield {
      storage.getOrElseUpdate(path, Set.empty).filter(_.name == fileName).foreach(_.clear)
    }
  }

  override def removeFile(fileName: String): Try[Unit] = {
    for {
      (path, _) <- FileService.parseFileName(fileName)
    } yield {
      val oldFiles = storage.getOrElseUpdate(path, Set.empty)
      storage.update(path, oldFiles.filterNot(_.name == fileName))
    }
  }

  override def writeToFile(fileName: String, content: String): Try[Unit] = {
    for {
      (path, _) <- FileService.parseFileName(fileName)
      _ <- {
        val allFiles = storage.getOrElseUpdate(path, Set.empty)
        val (target, others) = allFiles.partition(_.name == fileName)
        val file = target.headOption.getOrElse(DummyFile(fileName))
        file.append(content)
        storage.put(path, others ++ Set(file))
        Success(())
      }
    } yield ()
  }
}
