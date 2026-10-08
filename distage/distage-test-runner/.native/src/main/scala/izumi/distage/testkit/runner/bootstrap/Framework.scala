package izumi.distage.testkit.runner.bootstrap

import izumi.distage.testkit.protocol.*
import izumi.distage.testkit.runner.TestApplication

import java.io.{BufferedReader, InputStreamReader}
import java.net.Socket
import java.nio.charset.StandardCharsets
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.concurrent.duration.Duration
import scala.util.control.NonFatal

final class Framework extends TargetFramework(new NativeTargetRuntime)

private[bootstrap] final class NativeTargetRuntime extends TargetRuntime {
  override val context: ExecutionContext = ExecutionContext.global
  override def newRunId(): RunId = {
    val random = new java.util.Random
    RunId(new java.util.UUID(random.nextLong(), random.nextLong()).toString)
  }
  override def await(completion: Future[Unit]): Unit = Await.result(completion, Duration.Inf)

  override def openControl(port: Int, application: TestApplication): Future[TargetControl] = Future {
    val socket = new Socket("127.0.0.1", port)
    val reader = new BufferedReader(new InputStreamReader(socket.getInputStream, StandardCharsets.UTF_8))
    val completion = Promise[Unit]()
    val monitor = new Object
    var closing = false
    val receiver = new Thread(() => {
      try {
        var frame = reader.readLine()
        while (frame != null) {
          ProtocolCodec.decode(frame).fold(error => throw new IllegalArgumentException(error.message), value => value) match {
            case command: ProtocolMessage.Cancel => await(application.accept(command))
            case _ => throw new IllegalArgumentException("Target input accepts cancellation commands only")
          }
          frame = reader.readLine()
        }
        require(monitor.synchronized(closing), "Target input closed before application completion")
        val _ = completion.success(())
      } catch {
        case NonFatal(cause) =>
          application.accept(ProtocolMessage.Cancel(application.run)).failed.foreach(error => throw error)(context)
          val _ = completion.tryFailure(cause)
          ()
      }
    }, "distage-target-input")
    receiver.start()
    new TargetControl {
      override def close(): Future[Unit] = Future {
        monitor.synchronized { closing = true }
        try {
          socket.shutdownInput()
          receiver.join()
          await(completion.future)
        } finally socket.close()
      }(context)
    }
  }(context)
}
