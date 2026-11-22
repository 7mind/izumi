package izumi.fundamentals.platform.files

import izumi.fundamentals.platform.functional.Identity

import scala.concurrent.duration.*
import scala.io.StdIn

object FileLockMutexProcess {
  def main(args: Array[String]): Unit = {
    require(args.length == 2, "expected two mutex names")
    val own = args(0)
    val other = args(1)
    val retryWait = 10.millis
    val watchdog = 10.seconds
    def mutex(name: String, onExisting: => Unit = ()) = FileLockMutex.allocate[Identity, Unit](
      filename = name,
      retryWait = retryWait,
      maxAttempts = 0,
      attemptLog = (_, _) => (),
      lockAlreadyExistedLog = onExisting,
    )(
      attempts => throw new IllegalStateException(s"unexpected exhaustion: $attempts"),
      _ => (),
    )

    val (_, unlock) = mutex(own).unsafeAllocate()
    var held = true
    var contender = Option.empty[Thread]
    def releaseHeld(): Unit = if (held) {
      unlock()
      held = false
      println("RELEASED")
    }

    try {
      println("HELD")
      var running = true
      while (running) {
        StdIn.readLine() match {
          case "WAIT" =>
            require(contender.isEmpty, "duplicate WAIT")
            val thread = new Thread(() => {
              try {
                mutex(other, println("WAITING")).use(_ => ())
                println("COMPLETE")
              } catch {
                case error: Throwable =>
                  println(s"ERROR $error")
                  error.printStackTrace(Console.err)
              }
            }, "mutex-contender")
            contender = Some(thread)
            thread.start()
          case "RELEASE" => releaseHeld()
          case "EXIT" | null => running = false
          case command => throw new IllegalArgumentException(s"unknown command: $command")
        }
      }
    } finally {
      releaseHeld()
      contender.foreach { thread =>
        thread.join(watchdog.toMillis)
        require(!thread.isAlive, "contender did not finish")
      }
      Console.out.flush()
    }
  }
}
