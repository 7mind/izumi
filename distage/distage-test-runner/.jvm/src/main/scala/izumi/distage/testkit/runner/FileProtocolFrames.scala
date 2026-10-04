package izumi.distage.testkit.runner

import izumi.distage.testkit.protocol.ProtocolCodec

import java.io.{BufferedReader, BufferedWriter, InputStreamReader, OutputStreamWriter}
import java.nio.charset.{CodingErrorAction, StandardCharsets}
import java.nio.file.{Files, Path, StandardOpenOption}
import scala.util.control.NonFatal

final class FileProtocolFrameSink private (writer: BufferedWriter) extends ProtocolFrameSink {
  private var closed = false
  private var failure = Option.empty[Throwable]

  override def writeFrame(frame: String): Unit = synchronized {
    require(!closed, "Protocol output channel is closed")
    failure.foreach(throw _)
    ProtocolFrames.validate(frame)
    try {
      writer.write(frame)
      writer.write('\n')
      writer.flush()
    } catch {
      case NonFatal(cause) => failure = Some(cause); throw cause
    }
  }

  override def close(): Unit = synchronized {
    if (!closed) {
      closed = true
      writer.close()
    }
  }
}

object FileProtocolFrameSink {
  def createNew(path: Path): FileProtocolFrameSink = {
    val encoder = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
    new FileProtocolFrameSink(new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE), encoder)))
  }
}

final class FileProtocolFrameSource private (reader: BufferedReader) extends ProtocolFrameSource {
  private var closed = false
  private var failure = Option.empty[Throwable]

  override def readFrame(): Option[String] = synchronized {
    require(!closed, "Protocol input channel is closed")
    failure.foreach(throw _)
    try readLine()
    catch {
      case NonFatal(cause) => failure = Some(cause); throw cause
    }
  }

  private def readLine(): Option[String] = {
    val frame = new java.lang.StringBuilder
    var character = reader.read()
    while (character != -1 && character != '\n') {
      require(character != '\r', "Protocol channel frame must use an LF delimiter")
      require(frame.length() < ProtocolCodec.MaxFrameCharacters, "Protocol channel frame exceeds its character limit")
      val _ = frame.append(character.toChar)
      character = reader.read()
    }
    if (character == -1) {
      require(frame.length() == 0, "Protocol channel ended inside a frame")
      None
    } else {
      val value = frame.toString
      ProtocolFrames.validate(value)
      Some(value)
    }
  }

  override def close(): Unit = synchronized {
    if (!closed) {
      closed = true
      reader.close()
    }
  }
}

object FileProtocolFrameSource {
  def open(path: Path): FileProtocolFrameSource = {
    val decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
    new FileProtocolFrameSource(new BufferedReader(new InputStreamReader(Files.newInputStream(path), decoder)))
  }
}
