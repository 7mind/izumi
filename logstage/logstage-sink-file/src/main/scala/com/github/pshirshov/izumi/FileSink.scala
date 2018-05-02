package com.github.pshirshov.izumi

import java.util.concurrent.atomic.AtomicReference

import com.github.pshirshov.izumi.TryOps._
import com.github.pshirshov.izumi.logstage.api.logger.RenderingPolicy
import com.github.pshirshov.izumi.logstage.model.Log
import com.github.pshirshov.izumi.logstage.model.logger.LogSink
import com.github.pshirshov.izumi.models.FileRotation.{DisabledRotation, FileLimiterRotation}
import com.github.pshirshov.izumi.models.{FileRotation, FileSinkState, LogFile}

import scala.util.{Failure, Success, Try}

case class FileSinkConfig(maxAllowedSize: Int)

case class FileSink[T <: LogFile](
                                   renderingPolicy: RenderingPolicy
                                   , fileService: FileService[T]
                                   , rotation: FileRotation
                                   , config: FileSinkConfig
                                 ) extends LogSink {

  val sinkState: AtomicReference[FileSinkState] = {
    for {
      restoredMaybe <- restoreSinkState()
      state <- Success(restoredMaybe.getOrElse(initState))
    } yield {
      new AtomicReference[FileSinkState](state)
    }
  }.get

  def initState: FileSinkState = FileSinkState(currentFileId = 0, currentFileSize = 0)

  def restoreSinkState(): Try[Option[FileSinkState]] = {
    for {
      filesWithSize <- fileService.scanDirectory.map(f => fileService.fileSize(f).map((f, _))).asTry
      maybeFile <- Success(filesWithSize.toList.sortWith(_._2 < _._2).headOption) // by size ? // todo : bytes
      res <- Success(maybeFile.map {
        case (_, size) if size >= config.maxAllowedSize =>
          (filesWithSize.size, 0)
        case (name, size) =>
          (name, size)
      })
    } yield res.map {
      case (id, size) => FileSinkState(id, size)
    }
  }


  def processCurrentFile(state: FileSinkState): Try[FileSinkState] = {
    (state.currentFileId, state.currentFileSize) match {
      case (fileId, size) if size >= config.maxAllowedSize =>
        Success(state.copy(currentFileId = fileId + 1, currentFileSize = 0))
      case _ =>
        Success(state)
    }
  }

  def adjustByRotate(state: FileSinkState, rotate: FileRotation): Try[FileSinkState] = {
    rotation match {
      case DisabledRotation => Success(state)
      case FileLimiterRotation(limit) if state.currentFileId == limit =>
        val newFileId = 0
        val (_, rotateNext) = fileService.scanDirectory.partition(_ == newFileId)
        fileService.removeFile(newFileId).map {
          _ => initState.copy(forRotate = rotateNext)
        }
      case FileLimiterRotation(_) =>
        Success(state)
    }
  }

  def performWriting(state: FileSinkState, payload: String): Try[FileSinkState] = {
    val (current, others) = state.forRotate.partition(_ == state.currentFileId)
    current.headOption foreach fileService.removeFile
    for {
      _ <- fileService.writeToFile(state.currentFileId, payload)
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

