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

  def processCurrentFile(state: SinkState): Try[SinkState] = {
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
        Success {
          st.currentFileSize match {
            case size if size == st.getMaxSize =>
              st.copy(currentFileId = curFileId + 1, currentFileSize = FileSize.init)
            case _ =>
              st
          }
        }
    }
  }

  def adjustByRotation(state: SinkState): Try[SinkState] = {
    state.config.rotation.performRotate(state)
  }

  def writeLogEntry(oldState: SinkState, payload: String): Try[SinkState] = {
    val fileId = oldState.currentFileId
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
      _ <- updatedFile.write(payload)
    } yield updatedState.copy(currentFileSize = updatedState.currentFileSize + 1)
  }

  override def flush(e: Log.Entry): Unit = synchronized {
    val currState = sinkState.get
    (for {
      state1 <- processCurrentFile(currState)
      state2 <- adjustByRotation(state1)
      state3 <- writeLogEntry(state2, policy.render(e))
    } yield state3) match {
      case Failure(f) =>
        consoleSink.flush(e)
      case Success(newState) =>
        sinkState.set(newState)
    }
  }
}
