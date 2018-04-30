package com.github.pshirshov.izumi

import java.util.concurrent.atomic.AtomicReference
import com.github.pshirshov.izumi.models._
import com.github.pshirshov.izumi.logstage.api.logger.RenderingPolicy
import com.github.pshirshov.izumi.logstage.model.Log
import com.github.pshirshov.izumi.logstage.model.logger.LogSink
import com.github.pshirshov.izumi.logstage.sink.console.ConsoleSink
import scala.util.{Failure, Success, Try}


class FileSink(policy: RenderingPolicy, consoleSink: ConsoleSink, config: FileSinkConfig) extends LogSink {

  private val sinkState = new AtomicReference[SinkState](SinkState(config = config))

  def selectTargetFileId(state: SinkState): Try[(FileId, SinkState)] = {

    val curFileId = state.currentFileId

    val updatedStatus = {
      if (state.currentFileSize == FileSize.undefined) {
        state.getFile(curFileId).flatMap {
          file =>
            file.getFileSize.map {
              case size if size > 0 =>
                state.config.rotation.filesLimit.map(l => {
                  (curFileId + 1) to l map {
                    id => state.removeFile(id)
                  }
                })
                state.copy(currentFileSize = size)
              case size =>
                state.copy(currentFileSize = size)
            }
        }
      } else {
        Success(state)
      }
    }

    updatedStatus.flatMap {
      st =>
        st.currentFileSize match {
          case size if size == st.getMaxSize =>
            val newId = curFileId + 1
            Success((newId, st.copy(currentFileId = newId, currentFileSize = FileSize.init)))
          case _ =>
            Success((curFileId, st))
        }
    }
  }

  def adjustByRotation(fileId: FileId, state: SinkState): Try[(FileId, SinkState)] = {
    state.config.rotation.rotateFileId(fileId, state)
  }

  def writeLogEntry(fileId: FileId, oldState: SinkState, payload: String): Try[(Unit, SinkState)] = {
    for {
      curFile <- oldState.getFile(fileId)
      (updatedFile, updatedState) <- curFile.status match {
        case LogFileStatus.Rewrite =>
          for {
            _ <- oldState.removeFile(fileId)
            file <- oldState.getFile(fileId)
          } yield {
            (file, oldState)
          }
        case LogFileStatus.FirstEntry =>
          Success((curFile, oldState))
      }
      res <- updatedFile.write(payload)
    } yield (res, updatedState.copy(currentFileSize = updatedState.currentFileSize + 1))
  }

  override def flush(e: Log.Entry): Unit = synchronized {
    val currState = sinkState.get
    (for {
      (id1, state1) <- selectTargetFileId(currState)
      (id2, state2) <- adjustByRotation(id1, state1)
      (_, state3) <- writeLogEntry(id2, state2, policy.render(e))
    } yield state3) match {
      case Failure(f) =>
        consoleSink.flush(e)
      case Success(newState) =>
        sinkState.set(newState)
    }
  }
}
