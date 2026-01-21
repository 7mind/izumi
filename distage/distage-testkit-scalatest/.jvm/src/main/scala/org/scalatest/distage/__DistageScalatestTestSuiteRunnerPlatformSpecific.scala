package org.scalatest.distage

import izumi.distage.testkit.model.EnvResult
import izumi.distage.testkit.services.scalatest.dstest.TestRunnerRuntime.{AsyncGlobalSuitesControlHandle, AsyncResult}
import izumi.fundamentals.platform.console.TrivialLogger
import izumi.fundamentals.platform.language.Quirks.Discarder
import izumi.fundamentals.platform.strings.IzString.toRichIterable
import izumi.reflect.AnyTag

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

    val resultsPromise = Promise[List[EnvResult]]()

    resultCallback.apply {
      throwableOrResults =>
        asyncGlobalSuitesControl.completeOuterSuite(throwableOrResults.left.toOption)
        asyncGlobalSuitesControl.completeAllSuitesIfGlobal()

        resultsPromise.complete(throwableOrResults.toTry)

        throwableOrResults.foreach {
          testResults =>
            debugLogger.log(s"Got for ${tagMonoIO.tag}: testResults=${testResults.niceList()}")
        }
    }

    try {
      Await.result(resultsPromise.future, Duration.Inf).discard()
    } catch {
      case t: Throwable =>
        asyncGlobalSuitesControl.completeOuterSuite(Some(t))
    } finally {
      earlyShutdown.apply()
      asyncGlobalSuitesControl.completeOuterSuite(None)
      asyncGlobalSuitesControl.completeAllSuitesIfGlobal()
    }
  }

}
