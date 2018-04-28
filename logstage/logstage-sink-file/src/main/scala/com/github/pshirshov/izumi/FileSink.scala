package com.github.pshirshov.izumi

import java.io._
import java.nio.file.{FileAlreadyExistsException, Files, Paths, StandardOpenOption}
import java.util.concurrent.atomic.AtomicReference

import com.github.pshirshov.izumi.FileSink.{IOState, IOStateMod, State}
import com.github.pshirshov.izumi.Rotation.EnabledRotation
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.logstage.api.logger.RenderingPolicy
import com.github.pshirshov.izumi.logstage.model.Log
import com.github.pshirshov.izumi.logstage.model.logger.LogSink
import com.github.pshirshov.izumi.logstage.sink.console.ConsoleSink

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
    if (state.isFull) {
      State.changeFile(state, fileId + 1)
    } else {
      State(fileId, state)
    }
  }

  def rotateFile(fileId: Int, state: State): IOState[Int] = {
    state.config.rotation match {
      case EnabledRotation(limit) if state.currentFileId == limit =>
        for {
          res@(_, newState) <- State.changeFile(state, State.initFileId)
          _ <- Success(newState.unregisterNewFileWriter())
        } yield res

      case _ =>
        State.unchanged(fileId, state)
    }
  }

  def doWrite(fileId: Int, e: Log.Entry, state: State): IOStateMod = {
    for {
      _ <- state.writeNewItem(s"${policy.render(e)}\n")
      updatedState <- state.copy(currentFileSize = state.currentFileSize + 1)
    } yield updatedState
  }

  override def flush(e: Log.Entry): Unit = synchronized {
    val currState = sinkState.get
    (for {
      (file, afterPrepare) <- prepareFileId(currState)
      (rotatedFileId, afterRotate) <- rotateFile(file, afterPrepare)
      (_, afterWrite) <- doWrite(rotatedFileId, e, afterRotate)
    } yield afterWrite) match {
      case Failure(f) =>
        consoleSink.flush(e)
      case Success(newState) =>
        sinkState.set(newState)
    }
  }
}


object FileSink {

  type IOState[+A] = Try[(A, State)]

  type IOStateMod = IOState[Unit]

  case class State private (currentFileSize: Int = 0, currentFileId: Int = State.initFileId, config: FileSinkConfig, connectionPool : scala.collection.mutable.HashMap[String, OutputStream] = scala.collection.mutable.HashMap.empty[String, (OutputStream)]) {
    def name: String = State.buildName(config.destination, currentFileId)

    def writeNewItem(e : String) : Try[Unit] = {
      for {
        writer <- fetchFileData(name)
        _ <- Try(writer.write(e.getBytes())) // todo : UTF-8
      } yield ()
    }

    def fetchFileData(fileName : String = name) : Try[OutputStream] = {
      Try(connectionPool(fileName)) match {
        case s@Success(_) => s
        case Failure(f) =>
          for {
            _ <- createFileIfNotExists(fileName)
            stream <- Try(Files.newOutputStream(Paths.get(name), StandardOpenOption.APPEND))
          } yield {
            connectionPool.put(name, stream)
            stream
          }
      }
    }

    def unregisterNewFileWriter(filename : String = name) : Try[Unit] = {
      for {
        writeStream <- Try(connectionPool(filename))
        _ <- Try(writeStream.flush())
        _ <- Try(writeStream.close())
        _ <-  Try {
          Files.deleteIfExists(Paths.get(filename))
        }
        _ <- Success(connectionPool.remove(filename))
      } yield ()
    }

    def createFileIfNotExists(path : String) : Try[Unit] = {
      try {
        val res = Files.createFile(Paths.get(path))
        Success(Quirks.discard(res))
      } catch {
        case e : FileAlreadyExistsException =>
          Success(Quirks.discard())
        case other =>
          Failure(other)
      }
    }

    lazy val isFull: Boolean = config.fileLimit == currentFileSize
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
      State.apply(fileId, curState.copy(0, fileId, curState.config))
    }
  }

  implicit def stateMod(t: State): IOStateMod = {
    Success(((), t))
  }

  implicit def stateMode(t : Try[State]) : IOStateMod = {
    t.flatMap(_t => _t)
  }

}
