package com.github.pshirshov.izumi

import java.util.concurrent.atomic.AtomicReference

import com.github.pshirshov.izumi.FileSink.WithSize
import com.github.pshirshov.izumi.TryOps._
import com.github.pshirshov.izumi.logstage.api.logger.RenderingPolicy
import com.github.pshirshov.izumi.logstage.model.Log
import com.github.pshirshov.izumi.logstage.model.logger.LogSink
import com.github.pshirshov.izumi.models.FileRotation.{DisabledRotation, FileLimiterRotation}
import com.github.pshirshov.izumi.models.{FileRotation, FileSinkState}

import scala.util.{Failure, Success, Try}

case class FileSinkConfig(fileSize : Int)

case class FileSink(
                   renderingPolicy: RenderingPolicy
                    , fileService: FileService
                    , rotation: FileRotation
                    , config : FileSinkConfig
                   ) extends LogSink {

  val sinkState = new AtomicReference[FileSinkState](FileSinkState(config.fileSize))

  def processCurrentFile(state: FileSinkState): Try[FileSinkState] = {
    (state.currentFileId, state.currentFileSize) match {
      case (None, _) =>
        for {
          files <- fileService.getFileIds
          filesWithSize <- files.map(f => fileService.fileSize(f).map(WithSize(f, _))).asTry
          maybeFile <- Success(filesWithSize.toList.sortWith(_.size < _.size).headOption)
          (curFileId, curFileSize) <- maybeFile.map {
            case WithSize(_, size) if size >= state.maxSize => // if all files are full, target file will be with next id
              Success((filesWithSize.size, FileSink.FileSize.init))
            case WithSize(name, size) =>
              Success((name, size))
          }.getOrElse(Success((FileSink.FileIdentity.init, FileSink.FileSize.init)))
        } yield state.copy(currentFileId = Some(curFileId), currentFileSize = curFileSize)
      case (fileId@Some(_), size) if size >= state.maxSize =>
        Success(state.copy(currentFileId = fileId.map(_ + 1), currentFileSize = FileSink.FileSize.init))
      case _ =>
        Success(state)
    }
  }

  def adjustByRotate(state: FileSinkState, rotate: FileRotation): Try[FileSinkState] = {
    rotation match {
      case DisabledRotation => Success(state)
      case FileLimiterRotation(limit) if state.currentFileId.contains(limit) =>
        val newFileId = FileSink.FileIdentity.init
        for {
          (_, othersIds) <- fileService.getFileIds.map(_.partition(_ == newFileId))
          _ <- fileService.removeFile(newFileId)

        } yield {
          state.copy(currentFileId = Some(newFileId),
            currentFileSize = FileSink.FileSize.init,
            forRotate = othersIds
          )
        }
      case FileLimiterRotation(_) =>
        Success(state)
    }
  }

  def performWriting(state: FileSinkState, payload: String): Try[FileSinkState] = {
    for {
      curFileId <- Try(state.currentFileId.get)
      (current, others) <- Success(state.forRotate.partition(_ == curFileId))
      _ <- Try {
        current.headOption.foreach { _ => fileService.removeFile(curFileId) }
      }
      _ <- fileService.writeToFile(curFileId, payload)
    } yield state.copy(currentFileSize = state.currentFileSize + 1, forRotate = others)
  }

  override def flush(e: Log.Entry): Unit = synchronized {
    val oldState = sinkState.get()
    val res = for {
      s1 <- processCurrentFile(oldState)
      s2 <- adjustByRotate(s1, rotation)
      s3 <- performWriting(s2, renderingPolicy.render(e))
    } yield s3
    res match {
      case Failure(f) =>
      case Success(newState) =>
        sinkState.set(newState)
    }

  }
}

object FileSink {
  type FileIdentity = Int

  def buildFileName(path: String, id: FileIdentity): String = {
    s"$path/$id.txt"
  }

  object FileIdentity {
    final val init = 0
  }

  object FileSize {
    final val init = 0
  }

  case class WithSize[T](item: T, size: Int)
}


object TryOps {

  implicit class ListHelper[T](list: Iterable[Try[T]]) {
    def asTry: Try[Iterable[T]] = {
      Success(list.foldLeft(List.empty[T]) {
        case (acc, Success(item)) => acc :+ item
        case (_, Failure(message)) =>
          return Failure(throw new Exception(s"Error while parsing list of tries.Cause: $message"))
      })
    }
  }

}

