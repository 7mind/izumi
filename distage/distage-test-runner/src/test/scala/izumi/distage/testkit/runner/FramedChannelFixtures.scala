package izumi.distage.testkit.runner

import izumi.distage.testkit.protocol.*
import izumi.distage.testkit.runner.spec.AnyWordSpec

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object FramedChannelFixtures {
  trait Channel extends AutoCloseable {
    def sink: ProtocolFrameSink
    def source(): ProtocolFrameSource
  }

  def memory(): Channel = new Channel {
    private var frames = Vector.empty[String]
    private var closed = false
    override val sink: ProtocolFrameSink = new ProtocolFrameSink {
      override def writeFrame(frame: String): Unit = synchronized {
        require(!closed, "Protocol output channel is closed")
        ProtocolFrames.validate(frame)
        frames :+= frame
      }
      override def close(): Unit = synchronized { closed = true }
    }
    override def source(): ProtocolFrameSource = {
      val snapshot = sink.synchronized(frames)
      new ProtocolFrameSource {
        private var position = 0
        private var inputClosed = false
        override def readFrame(): Option[String] = synchronized {
          require(!inputClosed, "Protocol input channel is closed")
          if (position == snapshot.size) None
          else { val frame = snapshot(position); position += 1; Some(frame) }
        }
        override def close(): Unit = synchronized { inputClosed = true }
      }
    }
    override def close(): Unit = sink.close()
  }

  def run(make: () => Channel, label: String, context: ExecutionContext, verify: (Boolean, String) => Unit): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    def check(condition: Boolean, message: String): Unit = verify(condition, label + " " + message)
    val identity = CatalogueIdentity(BuildId("framed-build"), BuildTargetId("framed-target"), CatalogueId("framed-catalogue"))
    val request = RunRequest(identity, Selection.All, RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit))
    val messages = Vector[ProtocolMessage](
      ProtocolMessage.Discover(RunId("list"), identity.build, identity.target),
      ProtocolMessage.Cancel(RunId("雪\n\"\\\uD800")),
      ProtocolMessage.Request(RequestOperation.Resolve, RunId("saved"), request),
    )
    val channel = make()
    try {
      val output = new FramedProtocolOutput(channel.sink)
      messages.foreach(output.accept)
      val invalid = Vector("", "a\nb", "a\rb", "\uD800", "\uDC00", "x" * (ProtocolCodec.MaxFrameCharacters + 1))
      invalid.foreach(frame => check(Try(channel.sink.writeFrame(frame)).failed.toOption.exists(_.isInstanceOf[IllegalArgumentException]), "rejects invalid frame before writing"))
      check(Try(output.accept(ProtocolMessage.Cancel(RunId("")))).isFailure, "rejects an invalid message before writing")
      channel.sink.close()
      channel.sink.close()
      check(Try(output.accept(messages.head)).isFailure, "rejects writes after close")
      val source = channel.source()
      try {
        messages.foreach { message =>
          val frame = source.readFrame().getOrElse(throw new IllegalStateException("Missing framed message"))
          check(ProtocolCodec.decode(frame) == Right(message), "retains message identity through the channel")
        }
        check(source.readFrame().isEmpty && source.readFrame().isEmpty, "reports clean EOF without inventing a terminal outcome")
        source.close()
        source.close()
        check(Try(source.readFrame()).isFailure, "rejects reads after close")
      } finally source.close()
    } finally channel.close()

    val applicationChannel = make()
    val bodies = new AtomicInteger(0)
    final class FramedSuite extends AnyWordSpec {
      "stdout" should { "remain separate" in {
        println("APPLICATION_TEST_STDOUT is not a protocol frame")
        val _ = bodies.incrementAndGet()
      } }
    }
    val run = RunId("framed-application")
    val application = new TestApplication(run, identity, Vector(() => new FramedSuite), context, new FramedProtocolOutput(applicationChannel.sink))
    application.accept(ProtocolMessage.Discover(run, identity.build, identity.target)).flatMap { _ =>
      application.accept(ProtocolMessage.Request(RequestOperation.Plan, run, request))
    }.flatMap { _ =>
      application.accept(ProtocolMessage.Request(RequestOperation.Execute, run, request))
    }.map { _ =>
      applicationChannel.sink.close()
      val source = applicationChannel.source()
      val received = try {
        val frames = Vector.newBuilder[ProtocolMessage]
        var next = source.readFrame()
        while (next.nonEmpty) {
          frames += ProtocolCodec.decode(next.get).fold(error => throw new IllegalStateException(error.message), value => value)
          next = source.readFrame()
        }
        frames.result()
      } finally source.close()
      val discovered = received.collect { case message: ProtocolMessage.Discovered => message.catalogue.tests.map(_.id) }
      val planned = received.collect { case message: ProtocolMessage.Planned => message.plan.selection.tests.map(_.id) }
      val outcomes = received.collect { case message: ProtocolMessage.Completed => message.outcome }
      check(discovered.size == 1 && planned == discovered && outcomes.map(_.results.map(_.id)) == discovered, "list, plan and execution agree through framed output")
      check(outcomes.size == 1 && outcomes.head.successful && bodies.get() == 1, "executes one body and reports its terminal outcome")
      val events = received.collect { case event: ProtocolMessage.Event => event }
      check(events.map(_.sequence) == events.indices.map(_.toLong).toVector && events.last.event == RunEvent.Finished(run, outcomes.head), "preserves correlated ordered events and finalized completion")
      check(received.last == ProtocolMessage.Completed(outcomes.head), "keeps terminal completion last despite ordinary stdout")
      println("FRAMED_CHANNEL_CONTRACTS_OK adapter=" + label + " messages=" + messages.size + " application=completed stdout=separate")
    }.transform { result => applicationChannel.close(); result }
  }
}
