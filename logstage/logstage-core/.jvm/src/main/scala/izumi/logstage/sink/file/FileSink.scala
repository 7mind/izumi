package izumi.logstage.sink.file

import java.util.concurrent.atomic.AtomicReference

import izumi.logstage.api.Log
import izumi.logstage.api.logger.LogSink
import izumi.logstage.api.rendering.RenderingPolicy
import izumi.logstage.sink.file.models.FileRotation.{DisabledRotation, FileLimiterRotation}
import izumi.logstage.sink.file.models.{FileRotation, FileSinkConfig, FileSinkState, LogFile}

import scala.util.{Failure, Success, Try}

abstract class FileSink[T <: LogFile](
  val renderingPolicy: RenderingPolicy,
  val fileService: FileService[T],
  val rotation: FileRotation,
  val config: FileSinkConfig,
) extends LogSink {

  def recoverOnFail(e: String): Unit

  val sinkState: AtomicReference[FileSinkState] = {
    val currentState = restoreSinkState.getOrElse(initState)
    new AtomicReference[FileSinkState](currentState)
  }

  def initState: FileSinkState = FileSinkState(currentFileId = 0, currentFileSize = 0)

  def restoreSinkState: Option[FileSinkState] = {
    val files = fileService.scanDirectory
    val filesWithSize = files.map(f => (f, fileService.fileSize(f)))
    filesWithSize
      .toList.sortWith(_._2 < _._2).headOption.map {
        case (_, size) if size >= config.maxAllowedSize =>
          (filesWithSize.size, 0)
        case (name, size) =>
          (name, size)
      }.map {
        case (curFileId, curFileSize) => FileSinkState(curFileId, curFileSize)
      }
  }

  def processCurrentFile(state: FileSinkState): FileSinkState = {
    if (state.currentFileSize >= config.maxAllowedSize) {
      state.copy(currentFileId = state.currentFileId + 1, currentFileSize = 0)
    } else {
      state
    }
  }

  def adjustByRotate(state: FileSinkState): FileSinkState = {
    rotation match {
      case FileLimiterRotation(limit) if state.currentFileId == limit =>
        val newFileId = 0
        val (_, rotateNext) = fileService.scanDirectory.partition(_ == newFileId)
        fileService.removeFile(newFileId)
        initState.copy(forRotate = rotateNext)
      case FileLimiterRotation(_) | DisabledRotation =>
        state
    }
  }

  def performWriting(state: FileSinkState, payload: String): Try[FileSinkState] = {
    val (current, others) = state.forRotate.partition(_ == state.currentFileId)
    current.headOption foreach fileService.removeFile
    fileService.writeToFile(state.currentFileId, payload).map {
      _ =>
        state.copy(currentFileSize = state.currentFileSize + config.calculateMessageSize(payload), forRotate = others)
    }
  }

  override def flush(e: Log.Entry): Unit = synchronized {
    val renderedMessage = renderingPolicy.render(e)

    val oldState = sinkState.get()
    val res = for {
      s1 <- Try(processCurrentFile(oldState))
      s2 <- Try(adjustByRotate(s1))
      s3 <- performWriting(s2, renderedMessage)
    } yield s3
    res match {
      case Failure(f) =>
        recoverOnFail(s"Error while writing log to file. Cause: ${f.toString}")
        recoverOnFail(renderedMessage)
      case Success(newState) =>
        sinkState.set(newState)
    }
  }

}

object FileSink {
  type FileIdentity = Int
}
