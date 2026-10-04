package izumi.distage.sbt

import izumi.distage.sbt.DistageTestkitPlugin.autoImport._

import sbt._
import sbt.Keys._
import sbt.complete.DefaultParsers.spaceDelimited

private[sbt] object HostSettings {
  def settings: Seq[Def.Setting[?]] = Seq(
    distageBuildId := thisProjectRef.value.build.toString,
    distageTargetId := thisProjectRef.value.project + "/" + configuration.value.name,
    testFrameworks ~= { frameworks => if (frameworks.contains(DistageHostPolicy.framework)) frameworks else frameworks :+ DistageHostPolicy.framework },
    distageCatalogueId := DistageHostPolicy.catalogueId(definedTests.value),
    testOptions := DistageHostPolicy.withIdentity(testOptions.value, DistageHostPolicy.arguments(distageBuildId.value, distageTargetId.value, distageCatalogueId.value)),
    testOnly / HostReceiptPolicy.owner := HostReceiptPolicy.ownerAt(target.value),
    testQuick / HostReceiptPolicy.owner := HostReceiptPolicy.ownerAt(target.value),
    executeTests / HostReceiptPolicy.owner := HostReceiptPolicy.ownerAt(target.value),
    testOnly / testFilter := new HostSelectionObserver((testOnly / testFilter).value, HostReceiptPolicy.names(definedTests.value), (testOnly / HostReceiptPolicy.owner).value),
    testQuick / testFilter := {
      val filter = DistageHostPolicy.conservativeFilter(definedTests.value, (testQuick / testFilter).value, (testOnly / testFilter).value, streams.value.log)
      new HostSelectionObserver(filter, HostReceiptPolicy.names(definedTests.value), (testQuick / HostReceiptPolicy.owner).value)
    },
    testOnly / testExecution := HostReceiptPolicy.execution((testOnly / testExecution).value, definedTests.value, (testOnly / HostReceiptPolicy.owner).value, full = false),
    testQuick / testExecution := HostReceiptPolicy.execution((testQuick / testExecution).value, definedTests.value, (testQuick / HostReceiptPolicy.owner).value, full = false),
    test / testExecution := HostReceiptPolicy.execution((test / testExecution).value, definedTests.value, (executeTests / HostReceiptPolicy.owner).value, full = true),
    testOnly / testResultLogger := HostReceiptPolicy.logger((testOnly / testResultLogger).value, (testOnly / HostReceiptPolicy.owner).value),
    testQuick / testResultLogger := HostReceiptPolicy.logger((testQuick / testResultLogger).value, (testQuick / HostReceiptPolicy.owner).value),
    HostTaskBoundary.input(testOnly, testOnly / HostReceiptPolicy.owner),
    HostTaskBoundary.input(testQuick, testQuick / HostReceiptPolicy.owner),
    HostTaskBoundary.output(executeTests, executeTests / HostReceiptPolicy.owner),
    distageList := {
      val options = spaceDelimited("distage request options").parsed
      val identities = DistageHostPolicy.arguments(distageBuildId.value, distageTargetId.value, distageCatalogueId.value)
      val arguments = DistageHostPolicy.inspectionArguments("list", identities, options, definedTests.value)
      (run / runner).value.run(DistageHostPolicy.inspectionLauncher, fullClasspath.value.files, arguments, streams.value.log).get
    },
    distagePlan := {
      val options = spaceDelimited("distage request options").parsed
      val identities = DistageHostPolicy.arguments(distageBuildId.value, distageTargetId.value, distageCatalogueId.value)
      val arguments = DistageHostPolicy.inspectionArguments("plan", identities, options, definedTests.value)
      (run / runner).value.run(DistageHostPolicy.inspectionLauncher, fullClasspath.value.files, arguments, streams.value.log).get
    },
  )
}
