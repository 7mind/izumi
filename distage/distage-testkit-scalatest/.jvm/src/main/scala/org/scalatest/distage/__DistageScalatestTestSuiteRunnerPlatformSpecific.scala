package org.scalatest.distage

import izumi.distage.testkit.model.EnvResult
import izumi.distage.testkit.services.scalatest.dstest.TestRunnerRuntime.{AsyncGlobalSuitesControlHandle, AsyncResult}
import izumi.fundamentals.platform.console.TrivialLogger
import izumi.fundamentals.platform.strings.IzString.toRichIterable
import izumi.reflect.AnyTag

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Promise}

private[distage] object __DistageScalatestTestSuiteRunnerPlatformSpecific {

  /**
    * On JVM we always have to block in test runner, even if the test runner
    * runtime is async, so that we can receive and propagate a Ctrl-C interrupt
    * from sbt console. If none of the SBT test runner threads are blocking,
    * then no one would be able to receive the interrupt.
    */
  def handleAsyncTestRunnerPlatformSpecific(
    debugLogger: TrivialLogger,
    asyncGlobalSuitesControl: AsyncGlobalSuitesControlHandle,
    asyncResult: AsyncResult[List[EnvResult]],
    tagMonoIO: AnyTag,
  ): Unit = {
    val AsyncResult(resultCallback, earlyShutdown) = asyncResult

    val resultsPromise = Promise[Either[Throwable, List[EnvResult]]]()

    resultCallback.apply {
      throwableOrResults =>
        resultsPromise.success(throwableOrResults)

        throwableOrResults.foreach {
          testResults =>
            debugLogger.log(s"Got for ${tagMonoIO.tag}: testResults=${testResults.niceList()}")
        }
    }

    val interruptionFailures = ListBuffer.empty[Throwable]
    var interrupted = false
    var result = Option.empty[Either[Throwable, List[EnvResult]]]
    try {
      while (result.isEmpty) {
        try {
          result = Some(Await.result(resultsPromise.future, Duration.Inf))
        } catch {
          case cause: InterruptedException =>
            interrupted = true
            interruptionFailures += cause
            try earlyShutdown.apply()
            catch { case failure: Throwable => interruptionFailures += failure }
        }
      }

      val failures = result.get.left.toOption.toList ++ interruptionFailures.toList
      val failure = failures match {
        case Nil => None
        case cause :: Nil => Some(cause)
        case primary :: additional =>
          val combined = new RuntimeException("Multiple failures while awaiting test runtime completion", primary)
          additional.foreach(combined.addSuppressed)
          Some(combined)
      }
      asyncGlobalSuitesControl.completeOuterSuite(failure)
      asyncGlobalSuitesControl.completeAllSuitesIfGlobal()
    } finally {
      if (interrupted) Thread.currentThread().interrupt()
    }
  }

}
