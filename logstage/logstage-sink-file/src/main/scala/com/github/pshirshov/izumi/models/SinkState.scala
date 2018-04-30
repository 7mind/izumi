package com.github.pshirshov.izumi.models

import com.github.pshirshov.izumi.models.LogFile.RealFile

import scala.util.{Success, Try}


case class SinkState(currentFileId: FileId = FileId.init
                     , currentFileSize: Int = FileSize.undefined
                     , files: Files = scala.collection.mutable.HashMap.empty[FileId, RealFile]
                     , config: FileSinkConfig) {


  def getFile(fileId: FileId): Try[RealFile] = {
    Try(files.getOrElseUpdate(fileId, RealFile(name = fileId, path = config.destination, status = LogFileStatus.FirstEntry)))
  }

  def removeFile(fileId: FileId): Try[LogFile] = {
    for {
      file <- Try(files.getOrElseUpdate(fileId, RealFile(name = fileId, path = config.destination)))
      _ <- file.remove
      _ <- Success(files.remove(fileId))
    } yield file
  }

  def getMaxSize: FileId = config.fileSizeLimit
}