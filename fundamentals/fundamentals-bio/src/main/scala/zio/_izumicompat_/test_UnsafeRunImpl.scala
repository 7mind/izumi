package zio._izumicompat_

import izumi.functional.bio.Exit
import izumi.functional.bio.Exit.ZIOExit
import zio.{Cause, Chunk, FiberId, FiberRef, StackTrace, Supervisor, Trace, Unsafe, ZIO, internal}
import zio._izumicompat_.__ZIOSucceedCompat.zioSucceed
import zio.internal.{FiberRuntime, FiberScope, OneShot}

import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

class test_UnsafeRunImpl[R](runtime: zio.Runtime[R]) {

  private def debugState(marker: String): Unit = {
    val thread = Thread.currentThread()
    println(
      s"[ZIORunner][$marker] thread=${thread.getName}:${thread.getId} isThreadInterrupted=${thread.isInterrupted}}"
    )
  }

  def v_good[E, A](io: => ZIO[R, E, A]): Exit[E, A] = {
    val interrupted = new AtomicBoolean(true)
    debugState("unsafeRunSync.enter")
    val effect = ZIOExit.ZIOSignalOnNoExternalInterruptFailure(io)(zioSucceed(interrupted.set(false)))
    val resultFuture = new CompletableFuture[zio.Exit[E, A]]()
    debugState("unsafeRunSync.beforeFork")
    val fiber = runtime.unsafe.fork(effect)(using implicitly[zio.Trace], Unsafe)
    debugState("unsafeRunSync.afterFork")
    fiber.unsafe.addObserver(
      exit => {
        val thread = Thread.currentThread()
        println(
          s"[ZIORunner][unsafeRunSync.observer] thread=${thread.getName}:${thread.getId} isInterrupted=${thread.isInterrupted} exitTag=${
              if (exit.isSuccess) "Success" else "Failure"
            }"
        )
        resultFuture.complete(exit)
        ()
      }
    )(using Unsafe)
    debugState("unsafeRunSync.afterAddObserver")
    var wasInterrupted = false
//      while (!resultFuture.isDone) {
    try {
      debugState("unsafeRunSync.loop.beforeGet")
      resultFuture.get()
    } catch {
      case _: InterruptedException =>
        debugState("unsafeRunSync.loop.caughtInterruptedException")
        wasInterrupted = true
        debugState("unsafeRunSync.loop.beforeInterruptFiber")
        import zio._izumicompat_.__ZIOOneShot.OneShot
        val interruptedOneShot = OneShot.make[zio.Exit[Nothing, zio.Exit[E, A]]]
        val interruptionFiber = runtime.unsafe.fork(fiber.interruptAs(FiberId.None))(using implicitly[zio.Trace], Unsafe)
        interruptionFiber.unsafe.addObserver(interruptedOneShot.set)(Unsafe)
        interruptedOneShot.get() // wait until interruption is finished
//            throw t
        debugState("unsafeRunSync.loop.afterInterruptFiber")
    }
//      }
    debugState("unsafeRunSync.loop.done")
    if (wasInterrupted) {
      debugState("unsafeRunSync.beforeRestoreThreadInterrupt")
      Thread.currentThread().interrupt()
      debugState("unsafeRunSync.afterRestoreThreadInterrupt")
    }
    val result = resultFuture.get()
    val converted = ZIOExit.toExit(result)(interrupted.get())
    debugState("unsafeRunSync.afterToExit")
    println(s"[ZIORunner][unsafeRunSync.converted] convertedClass=${converted.getClass.getName}")
    converted
  }

  def v_goodRunOrFork[E, A](io: => ZIO[R, E, A]): Exit[E, A] = {
    val interrupted = new AtomicBoolean(true)
    debugState("unsafeRunSync.enter")
    val effect = ZIOExit.ZIOSignalOnNoExternalInterruptFailure(io)(zioSucceed(interrupted.set(false)))
    debugState("unsafeRunSync.beforeRunOrFork")
    runtime.unsafe.runOrFork(effect)(using implicitly[zio.Trace], Unsafe) match {
      case Right(exit) =>
        debugState("unsafeRunSync.runOrFork.right")
        val converted = ZIOExit.toExit(exit)(interrupted.get())
        debugState("unsafeRunSync.afterToExit.right")
        println(s"[ZIORunner][unsafeRunSync.converted.right] convertedClass=${converted.getClass.getName}")
        converted
      case Left(fiber) =>
        val resultFuture = new CompletableFuture[zio.Exit[E, A]]()
        debugState("unsafeRunSync.runOrFork.left")
        fiber.unsafe.addObserver(
          exit => {
            val thread = Thread.currentThread()
            println(
              s"[ZIORunner][unsafeRunSync.observer] thread=${thread.getName}:${thread.getId} isInterrupted=${thread.isInterrupted} exitTag=${
                  if (exit.isSuccess) "Success" else "Failure"
                }"
            )
            resultFuture.complete(exit)
            ()
          }
        )(using Unsafe)
        debugState("unsafeRunSync.afterAddObserver")
        var wasInterrupted = false
        scala.concurrent.blocking {
          try {
            debugState("unsafeRunSync.loop.beforeGet")
            resultFuture.get()
          } catch {
            case _: InterruptedException =>
              debugState("unsafeRunSync.loop.caughtInterruptedException")
              wasInterrupted = true
              debugState("unsafeRunSync.loop.beforeInterruptFiber")
              import zio._izumicompat_.__ZIOOneShot.OneShot
              val interruptedOneShot = OneShot.make[zio.Exit[Nothing, zio.Exit[E, A]]]
              val interruptionFiber = runtime.unsafe.fork(fiber.interruptAs(FiberId.None))(using implicitly[zio.Trace], Unsafe)
              interruptionFiber.unsafe.addObserver(interruptedOneShot.set)(Unsafe)
              interruptedOneShot.get() // wait until interruption is finished
              debugState("unsafeRunSync.loop.afterInterruptFiber")
          }
        }
        debugState("unsafeRunSync.loop.done")
        if (wasInterrupted) {
          debugState("unsafeRunSync.beforeRestoreThreadInterrupt")
          Thread.currentThread().interrupt()
          debugState("unsafeRunSync.afterRestoreThreadInterrupt")
        }
        val result = resultFuture.get()
        val converted = ZIOExit.toExit(result)(interrupted.get())
        debugState("unsafeRunSync.afterToExit.left")
        converted
    }
  }

  def v_fixedInitial[E, A](io: => ZIO[R, E, A]): Exit[E, A] = {
    def interned_Fixed_Run[E0, A0](effect: ZIO[R, E0, A0])(implicit trace: Trace, unsafe: Unsafe): zio.Exit[E0, A0] = {
      runtime.unsafe.runOrFork(effect) match {
        case Left(fiber) =>
          import internal.OneShot
          val result = OneShot.make[zio.Exit[E0, A0]]
          fiber.unsafe.addObserver(result.set)
          try {
            scala.concurrent.blocking {
              result.get()
            }
          } catch {
            case t: InterruptedException =>
              fiber.tellInterrupt(Cause.interrupt(FiberId.None, StackTrace(FiberId.None, Chunk.single(trace))))
              result.get() // wait for fiber interruption to finish
              throw t
          }
        case Right(exit) =>
          exit
      }
    }

    val interrupted = new AtomicBoolean(true)
    val effect = ZIOExit.ZIOSignalOnNoExternalInterruptFailure(io)(zioSucceed(interrupted.set(false)))
    val zioExit = interned_Fixed_Run(effect)(using implicitly[zio.Trace], Unsafe)
    ZIOExit.toExit(zioExit)(interrupted.get())
  }

  def v_goodUnsafeRun[E, A](io: => ZIO[R, E, A]): Exit[E, A] = {
    def interned_Run[E0, A0](effect: ZIO[R, E0, A0])(implicit trace: Trace, unsafe: Unsafe): zio.Exit[E0, A0] = {
      runtime.unsafe.runOrFork(effect) match {
        case Left(fiber) =>
          import internal.OneShot
          val result = OneShot.make[zio.Exit[E0, A0]]
          fiber.unsafe.addObserver(result.set)
          scala.concurrent.blocking {
            try {
              result.get()
            } catch {
              case t: InterruptedException =>
                val interrupted = OneShot.make[zio.Exit[Nothing, zio.Exit[E0, A0]]]
                val interruptionFiber = makeFiber(fiber.interruptAs(FiberId.None))
                interruptionFiber.addObserver(interrupted.set)
                interruptionFiber.start(fiber.interruptAs(FiberId.None))
                interrupted.get()
                throw t
            }
          }
        case Right(exit) =>
          exit
      }
    }

    val interrupted = new AtomicBoolean(true)
    debugState("v_goodUnsafeRun.enter")
    val effect = ZIOExit.ZIOSignalOnNoExternalInterruptFailure(io)(zioSucceed(interrupted.set(false)))
    debugState("v_goodUnsafeRun.beforeRunOrFork")
    val exit = {
      runtime.unsafe.runOrFork(effect)(using implicitly[zio.Trace], Unsafe) match {
        case Right(exit) =>
          debugState("v_goodUnsafeRun.runOrFork.right")
          exit
        case Left(fiber) =>
          val result = OneShot.make[zio.Exit[E, A]]
//          val result = new CompletableFuture[zio.Exit[E, A]]()
          debugState("v_goodUnsafeRun.runOrFork.left")
          fiber.unsafe.addObserver {
            exit =>
              result.set(exit)
              val thread = Thread.currentThread()
              println(
                s"[ZIORunner][v_goodUnsafeRun.observer] thread=${thread.getName}:${thread.getId} isInterrupted=${thread.isInterrupted} exitTag=${
                    if (exit.isSuccess) "Success" else "Failure"
                  }"
              )
//              result.complete(exit)
              ()
          }(using Unsafe)
          debugState("v_goodUnsafeRun.afterAddObserver")
//          var wasInterrupted = false
          scala.concurrent.blocking {
            try {
              debugState("v_goodUnsafeRun.loop.beforeGet")
              result.get()
            } catch {
              case t: InterruptedException =>
                debugState("v_goodUnsafeRun.loop.caughtInterruptedException")
//                wasInterrupted = true
                debugState("v_goodUnsafeRun.loop.beforeInterruptFiber")
//                locally { // original ZIO .run
//                  val interrupted = OneShot.make[zio.Exit[Nothing, zio.Exit[E, A]]]
//                  val interruptionFiber = makeFiber(fiber.interruptAs(FiberId.None))(using implicitly[zio.Trace], Unsafe)
//                  interruptionFiber.addObserver(interrupted.set)(using Unsafe)
//                  interruptionFiber.start(fiber.interruptAs(FiberId.None))
//                  interrupted.get()
//                  throw t
//                }
//                locally { // original v_
//                  val interrupted = OneShot.make[zio.Exit[Nothing, zio.Exit[E, A]]]
//                  val interruptionFiber = runtime.unsafe.fork(fiber.interruptAs(FiberId.None))(using implicitly[zio.Trace], Unsafe)
//                  interruptionFiber.unsafe.addObserver(interrupted.set)(using Unsafe)
//                  interrupted.get() // wait until interruption is finished
//                }
                locally {
//                  val interrupted = OneShot.make[zio.Exit[Nothing, zio.Exit[E, A]]]
                  val interrupted = result
                  try
                    locally {
                      fiber.tellInterrupt(Cause.interrupt(FiberId.None, StackTrace(FiberId.None, Chunk.single(implicitly))))
//                      val interruptionFiber = makeFiber(fiber.interruptAsFork(FiberId.None))(using implicitly, Unsafe)
//                      interruptionFiber.addObserver(interrupted.set)(using Unsafe)
                      //                  Thread.interrupted()
                      //                  require(!Thread.interrupted())
//                      interruptionFiber.start(fiber.interruptAsFork(FiberId.None))
//                      interruptionFiber.startConcurrently(fiber.interruptAsFork(FiberId.None))
                    }
                  catch {
                    case xt: Throwable =>
                      val trdInt = Thread.currentThread().isInterrupted
                      Thread.interrupted() // clear interrupt for isInterrupted call
                      //                      val tgtFiberInt = fiber.asInstanceOf[internal.FiberRuntime[E, A]].isInterrupted()
                      //                      debugState(s"v_goodUnsafeRun.[not interrupted] due to tgtFiberInt=$tgtFiberInt unsafeRunThreadInt=$trdInt exc=$xt")
                      debugState(s"v_goodUnsafeRun.fiberStart.not interrupted due to unsafeRunThreadInt=$trdInt exc=$xt")
                  }
                  try interrupted.get() // wait until interruption is finished
                  catch {
                    case xt: Throwable =>
                      val trdInt = Thread.currentThread().isInterrupted
                      Thread.interrupted() // clear interrupt for isInterrupted call
                      val tgtFiberInt = fiber match {
                        case f: internal.FiberRuntime[E, A] => Some(f.isInterrupted())
                        case _ => None
                      }
                      debugState(
                        s"v_goodUnsafeRun.get.not interrupted due to unsafeRunThreadInt=$trdInt tgtFiberInt=$tgtFiberInt oneShotState=${interrupted.isSet} exc=$xt"
                      )
                  }
                }
                debugState("v_goodUnsafeRun.loop.afterInterruptFiber")
                throw t
            }
          }
          debugState("v_goodUnsafeRun.loop.done")
//          if (wasInterrupted) {
//            debugState("v_goodUnsafeRun.beforeRestoreThreadInterrupt")
//            Thread.currentThread().interrupt()
//            debugState("v_goodUnsafeRun.afterRestoreThreadInterrupt")
//          }
          result.get()
      }
    }
    ZIOExit.toExit(exit)(interrupted.get())
  }

  def v_badInitial[E, A](io: => ZIO[R, E, A]): Exit[E, A] = {
    val interrupted = new AtomicBoolean(true)
    val result = runtime.unsafe.run {
      ZIOExit.ZIOSignalOnNoExternalInterruptFailure(io)(zioSucceed(interrupted.set(false)))
    }(using implicitly[zio.Trace], Unsafe)
    ZIOExit.toExit(result)(interrupted.get())
  }

  private def makeFiber[E, A](
    zio: ZIO[R, E, A]
  )(implicit trace: Trace,
    unsafe: Unsafe,
  ): internal.FiberRuntime[E, A] = {
    val fiberIdGen = runtime.fiberRefs.getOrDefault(FiberRef.currentFiberIdGenerator)
    val fiberId = fiberIdGen.make(trace)
    val fiberRefs = runtime.fiberRefs.updatedAs(fiberId)(FiberRef.currentEnvironment, runtime.environment)
    val fiber = FiberRuntime[E, A](fiberId, fiberRefs.forkAs(fiberId), runtime.runtimeFlags)

    FiberScope.global.add(null, runtime.runtimeFlags, fiber)

    val supervisor = fiber.getSupervisor()

    if (supervisor ne Supervisor.none) {
      supervisor.onStart(runtime.environment, zio, None, fiber)
    }

    fiber
  }

}
