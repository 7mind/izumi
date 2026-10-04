package izumi.distage.testkit.runner

import izumi.distage.testkit.protocol.{ProtocolCodec, ProtocolMessage, RunId}

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object FileProtocolFrameFixtures {
  def run(context: ExecutionContext): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    val checks = new AtomicInteger(0)
    def verify(condition: Boolean, message: String): Unit = {
      checks.incrementAndGet()
      require(condition, message)
    }
    FramedChannelFixtures.run(() => new FileChannel, "file", context, verify).flatMap(_ => ApplicationLauncherFixtures.run(() => new FileChannel, "file", context, verify)).map { _ =>
      val directory = Files.createTempDirectory("izumi-frame-boundaries-")
      val path = directory.resolve("channel.jsonl")
      try {
        val cases = Vector(
          "unterminated" -> "{}".getBytes(StandardCharsets.UTF_8),
          "empty" -> "\n".getBytes(StandardCharsets.UTF_8),
          "carriage return" -> "{}\r\n".getBytes(StandardCharsets.UTF_8),
          "oversized" -> (("x" * (ProtocolCodec.MaxFrameCharacters + 1)) + "\n").getBytes(StandardCharsets.UTF_8),
          "malformed UTF-8" -> Array[Byte](0xc3.toByte, 0x28.toByte, '\n'.toByte),
        )
        cases.foreach { case (label, bytes) =>
          writeBytes(path, bytes)
          val source = FileProtocolFrameSource.open(path)
          try {
            val first = Try(source.readFrame()).failed.get
            verify(Try(source.readFrame()).failed.toOption.exists(_ eq first), label + " must retain the first channel failure")
          } finally source.close()
        }
        writeBytes(path, (("x" * ProtocolCodec.MaxFrameCharacters) + "\n").getBytes(StandardCharsets.UTF_8))
        val source = FileProtocolFrameSource.open(path)
        try verify(source.readFrame().exists(_.length == ProtocolCodec.MaxFrameCharacters) && source.readFrame().isEmpty, "Exact character-limit frame must be bounded and readable")
        finally source.close()
        val original = Files.readAllBytes(path)
        verify(Try(FileProtocolFrameSink.createNew(path)).isFailure && java.util.Arrays.equals(Files.readAllBytes(path), original), "Output creation must preserve an existing channel")
        val unicode = ProtocolCodec.encode(ProtocolMessage.Cancel(RunId("雪\uD800")))
        writeBytes(path, (unicode + "\n").getBytes(StandardCharsets.UTF_8))
        val unicodeSource = FileProtocolFrameSource.open(path)
        try verify(unicodeSource.readFrame().exists(frame => ProtocolCodec.decode(frame) == Right(ProtocolMessage.Cancel(RunId("雪\uD800")))), "Physical UTF-8 transport preserves escaped lone surrogates")
        finally unicodeSource.close()
      } finally {
        val _ = Files.deleteIfExists(path)
        Files.delete(directory)
      }
      println("FILE_PROTOCOL_FRAME_FIXTURES_OK checks=" + checks.get() + " bounded=true truncated=rejected utf8=strict existing=preserved")
    }
  }

  private def writeBytes(path: Path, bytes: Array[Byte]): Unit = {
    val _ = Files.write(path, bytes)
    ()
  }

  private final class FileChannel extends FramedChannelFixtures.Channel {
    private val directory: Path = Files.createTempDirectory("izumi-frame-contract-")
    private val path = directory.resolve("channel.jsonl")
    override val sink: ProtocolFrameSink = FileProtocolFrameSink.createNew(path)
    override def source(): ProtocolFrameSource = FileProtocolFrameSource.open(path)
    override def close(): Unit = {
      sink.close()
      val _ = Files.deleteIfExists(path)
      Files.delete(directory)
    }
  }
}
