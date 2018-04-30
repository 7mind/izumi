package com.github.pshirshov.izumi.models

import scala.util.{Success, Try}

trait Rotation {
  def rotateFileId(fileId: FileId, state: SinkState): Try[(FileId, SinkState)]

  def filesLimit: Option[Int]
}

object Rotation {

  case object DisabledRotation extends Rotation {
    override def rotateFileId(fileId: FileId, state: SinkState): Try[(FileId, SinkState)] = {
      Success((fileId, state))
    }

    override lazy val filesLimit: Option[FileId] = None
  }

  case class EnabledRotation(limit: Int) extends Rotation {
    override def rotateFileId(fileId: FileId, oldState: SinkState): Try[(FileId, SinkState)] = {
      if (fileId == limit) {
        val newFileId = FileId.init
        val files = oldState.files
        Success((
          newFileId
          , oldState.copy(currentFileId = newFileId
          , currentFileSize = 0
          // mark all files
          , files = scala.collection.mutable.HashMap.empty ++ files.mapValues(_.copy(status = LogFileStatus.Rewrite)))))
      } else {
        Success((fileId, oldState))
      }
    }

    override lazy val filesLimit: Option[FileId] = Some(limit)
  }

}