package org.scalatest.distage

import izumi.distage.testkit.model.EnvResult
import izumi.distage.testkit.services.scalatest.dstest.TestRunnerRuntime.{AsyncGlobalSuitesControlHandle, AsyncResult}
import izumi.fundamentals.platform.console.TrivialLogger
import izumi.fundamentals.platform.strings.IzString.toRichIterable
import izumi.reflect.AnyTag

private[distage] object __DistageScalatestTestSuiteRunnerPlatformSpecific {

  def handleAsyncTestRunnerPlatformSpecific(
    debugLogger: TrivialLogger,
    asyncGlobalSuitesControl: AsyncGlobalSuitesControlHandle,
    asyncResult: AsyncResult[List[EnvResult]],
    tagMonoIO: AnyTag,
  ): Unit = {
    val AsyncResult(resultCallback, earlyShutdown) = asyncResult

    resultCallback {
      throwableOrResults =>
        asyncGlobalSuitesControl.completeOuterSuite(throwableOrResults.left.toOption)
        asyncGlobalSuitesControl.completeAllSuitesIfGlobal()

        throwableOrResults match {
          case Right(testResults) =>
            debugLogger.log(s"Got for ${tagMonoIO.tag}: testResults=${testResults.niceList()}")

          case Left(t) =>
            earlyShutdown.apply()
            throw t
        }
    }
  }

}
