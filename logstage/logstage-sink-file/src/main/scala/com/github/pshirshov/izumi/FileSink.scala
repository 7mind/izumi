package com.github.pshirshov.izumi

import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

import com.github.pshirshov.izumi.Rotation.{DisabledRotation, EnabledRotation}
import com.github.pshirshov.izumi.logstage.api.logger.RenderingPolicy
import com.github.pshirshov.izumi.logstage.model.Log
import com.github.pshirshov.izumi.logstage.model.logger.LogSink
import com.github.pshirshov.izumi.logstage.sink.console.ConsoleSink
import java.io._

import com.github.pshirshov.izumi.FileSink.{IOState, IOStateMod, State}

import scala.util.{Failure, Success, Try}


sealed trait Rotation {
  def enabled: Boolean
}

object Rotation {

  case object DisabledRotation extends Rotation {
    override val enabled: Boolean = false
  }

  case class EnabledRotation(limit: Int) extends Rotation {
    override val enabled: Boolean = true
  }

}


case class FileSinkConfig(fileLimit: Int, destination: String, rotation: Rotation)

class FileSink(policy: RenderingPolicy, consoleSink: ConsoleSink, config: FileSinkConfig) extends LogSink {
  private val sinkState = new AtomicReference[State](State(config = config))

  def prepareFileId(state: State): IOState[Int] = {
    val fileId = state.currentFileId
    if (state.isFilled) {
      State.changeFile(state, fileId + 1)
    } else {
      State.unchanged(fileId, state)
    }
  }

  def rotateFile(fileId: Int, state: State): IOState[Int] = {
    state.config.rotation match {
      case EnabledRotation(limit) if state.currentFileId == limit =>
        State.changeFile(state, State.initFileId)
      case _ =>
        State.unchanged(fileId, state)
    }
  }

  def doWrite(fileId: Int, e: Log.Entry, state: State): IOStateMod = {
    val res = for {
      _ <- Try(state.fileWriter.append(policy.render(e)))
      _ <- Try(state.fileWriter.newLine())
      _ <- Try(state.fileWriter.flush())
      ss <- State.mod(state) {
        s => s.copy(currentFileId = s.currentFileSize + 1)
      }
    } yield ss
    res
  }

  override def flush(e: Log.Entry): Unit = synchronized {
    val currState = sinkState.get
    (for {
      (file, afterPrepare) <- prepareFileId(currState)
      (rotatedFileId, afterRotate) <- rotateFile(file, afterPrepare)
      (_, afterWrite) <- doWrite(rotatedFileId, e, afterRotate)
    } yield afterWrite) match {
      case Failure(f) =>
        println("fa")
        // todo : also console output failure message ?
        consoleSink.flush(e)
      case Success(newState) =>
        sinkState.set(newState)
    }
  }
}


object FileSink {

  type IOState[+A] = Try[(A, State)]

  type IOStateMod = IOState[Unit]

  case class State(currentFileSize: Int = 0, currentFileId: Int = State.initFileId, config: FileSinkConfig) {
    lazy val fileWriter = new BufferedWriter(new FileWriter(State.buildName(config.destination, currentFileId)))
    def isFilled: Boolean = config.fileLimit == currentFileSize
  }

  object State {

    def apply[T](t: T, state: State): IOState[T] = Success((t, state))

    val initFileId = 0

    def unchanged[T](t: T, state: State): IOState[T] = {
      Success((t, state))
    }

    def mod(state: State)(f: State => IOState[Unit]): IOState[Unit] = {
      f(state)
    }

    def buildName(path: String, id: Int): String = {
      s"$path/log.$id.txt"
    }

    def changeFile(curState: State, fileId: Int): IOState[Int] = {
      for {
        (_, afterClose) <- State.mod(curState) {
          s =>
            val res = for {
              _ <- Try(s.fileWriter.flush())
              _ <- Try(s.fileWriter.close())
            } yield s
            res
        }
      } yield (fileId, afterClose.copy(0, fileId, afterClose.config))
    }
  }

  implicit def stateMod(t: State): IOStateMod = {
    Success(((), t))
  }

  implicit def stateMode(t : Try[State]) : IOStateMod = {
    t.flatMap(_t => _t)
  }

}

