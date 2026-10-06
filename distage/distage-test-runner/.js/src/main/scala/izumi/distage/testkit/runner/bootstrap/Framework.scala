package izumi.distage.testkit.runner.bootstrap

import izumi.distage.testkit.protocol.*
import izumi.distage.testkit.runner.TestApplication

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.scalajs.js

final class Framework extends TargetFramework(new JsTargetRuntime)

private[bootstrap] final class JsTargetRuntime extends TargetRuntime {
  override val context: ExecutionContext = scala.scalajs.concurrent.JSExecutionContext.queue
  override def newRunId(): RunId = RunId(js.Dynamic.global.crypto.randomUUID().asInstanceOf[String])
  override def await(completion: Future[Unit]): Unit = throw new UnsupportedOperationException("JS tasks require the asynchronous execution callback")

  override def openControl(port: Int, application: TestApplication): Future[TargetControl] = {
    implicit val ec: ExecutionContext = context
    val ready = Promise[TargetControl]()
    val closed = Promise[Unit]()
    val socket = js.Dynamic.global.require("net").connect(port, "127.0.0.1")
    var pending = ""
    var closing = false
    var failure = Option.empty[Throwable]
    def fail(cause: Throwable): Unit = {
      if (failure.isEmpty) failure = Some(cause)
      ready.tryFailure(cause)
      application.accept(ProtocolMessage.Cancel(application.run)).failed.foreach(error => throw error)
      val _ = socket.destroy()
    }
    socket.setEncoding("utf8")
    socket.on("connect", (() => {
      ready.success(new TargetControl {
        override def close(): Future[Unit] = {
          closing = true
          socket.destroy()
          closed.future
        }
      })
      ()
    }): js.Function0[Unit])
    socket.on("data", ((chunk: String) => {
      pending += chunk
      var newline = pending.indexOf('\n')
      while (newline >= 0) {
        val frame = pending.substring(0, newline)
        pending = pending.substring(newline + 1)
        ProtocolCodec.decode(frame) match {
          case Right(command: ProtocolMessage.Cancel) => application.accept(command).failed.foreach(fail)
          case Right(_) => fail(new IllegalArgumentException("Target input accepts cancellation commands only"))
          case Left(error) => fail(new IllegalArgumentException(error.message))
        }
        newline = pending.indexOf('\n')
      }
      if (pending.length > ProtocolCodec.MaxFrameCharacters) fail(new IllegalArgumentException("Target input exceeds the protocol frame limit"))
    }): js.Function1[String, Unit])
    socket.on("error", ((error: js.Dynamic) => fail(new IllegalStateException(error.toString))): js.Function1[js.Dynamic, Unit])
    socket.on("close", (() => {
      if (!closing && failure.isEmpty) failure = Some(new IllegalStateException("Target input closed before application completion"))
      failure match {
        case Some(cause) =>
          ready.tryFailure(cause)
          application.accept(ProtocolMessage.Cancel(application.run)).failed.foreach(error => throw error)
          closed.tryFailure(cause)
        case None => closed.trySuccess(())
      }
      ()
    }): js.Function0[Unit])
    ready.future
  }
}
