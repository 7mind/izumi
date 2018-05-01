package com.github.pshirshov.izumi.models

import scala.util.{Success, Try}

trait Rotation {
  def performRotate(state: SinkState): Try[SinkState]

  def filesLimit: Option[Int]
}

object Rotation {

  case object DisabledRotation extends Rotation {
    override def performRotate(state: SinkState): Try[SinkState] = {
      Success(state)
    }

    override lazy val filesLimit: Option[FileId] = None
  }

  case class EnabledRotation private(limit: Int) extends Rotation {
    override def performRotate(oldState: SinkState): Try[SinkState] = {
      if (oldState.currentFileId == limit) {
        val newFileId = FileId.init
        val files = oldState.files
        Success(
          oldState.copy(currentFileId = newFileId, currentFileSize = 0, files = scala.collection.mutable.HashMap.empty ++ files.mapValues(_.copy(status = LogFileStatus.Rewrite))))
      } else {
        Success(oldState)
      }
    }

    override lazy val filesLimit: Option[FileId] = Some(limit)
  }

}