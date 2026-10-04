package izumi.distage.sbt

import izumi.distage.sbt.DistageTestkitPlugin.autoImport.*

import sbt.*
import sbt.Keys.*
import sbt.complete.DefaultParsers.spaceDelimited
import sbt.util.{Digest, Logger}

private[sbt] object HostSettings {
  def settings: Seq[Def.Setting[?]] = Seq(
    distageBuildId := thisProjectRef.value.build.toString,
    distageTargetId := thisProjectRef.value.project + "/" + configuration.value.name,
    testFrameworks ~= { frameworks => if (frameworks.contains(DistageHostPolicy.framework)) frameworks else frameworks :+ DistageHostPolicy.framework },
    distageCatalogueId := Def.uncached { DistageHostPolicy.catalogueId(definedTests.value) },
    testOptions := Def.uncached { DistageHostPolicy.withIdentity(testOptions.value, DistageHostPolicy.arguments(distageBuildId.value, distageTargetId.value, distageCatalogueId.value)) },
    testSelected / HostReceiptPolicy.owner := HostReceiptPolicy.ownerAt(target.value),
    testQuick / HostReceiptPolicy.owner := HostReceiptPolicy.ownerAt(target.value),
    executeTests / HostReceiptPolicy.owner := HostReceiptPolicy.ownerAt(target.value),
    testSelected / definedTestDigests := Def.uncached { cacheableDigests((testSelected / definedTestDigests).value, definedTests.value, streams.value.log) },
    testQuick / definedTestDigests := Def.uncached { cacheableDigests((testQuick / definedTestDigests).value, definedTests.value, streams.value.log) },
    test / definedTestDigests := Def.uncached { cacheableDigests((test / definedTestDigests).value, definedTests.value, streams.value.log) },
    testSelected / testFilter := Def.uncached { new HostSelectionObserver((testSelected / testFilter).value, HostReceiptPolicy.names(definedTests.value), (testSelected / HostReceiptPolicy.owner).value) },
    testQuick / testFilter := Def.uncached {
      val filter = DistageHostPolicy.conservativeFilter(definedTests.value, (testQuick / testFilter).value, (testSelected / testFilter).value, streams.value.log)
      new HostSelectionObserver(filter, HostReceiptPolicy.names(definedTests.value), (testQuick / HostReceiptPolicy.owner).value)
    },
    testSelected / testExecution := Def.uncached { HostReceiptPolicy.execution((testSelected / testExecution).value, definedTests.value, (testSelected / HostReceiptPolicy.owner).value, full = false) },
    testQuick / testExecution := Def.uncached { HostReceiptPolicy.execution((testQuick / testExecution).value, definedTests.value, (testQuick / HostReceiptPolicy.owner).value, full = false) },
    test / testExecution := Def.uncached { HostReceiptPolicy.execution((test / testExecution).value, definedTests.value, (executeTests / HostReceiptPolicy.owner).value, full = true) },
    testSelected / testResultLogger := HostReceiptPolicy.logger((testSelected / testResultLogger).value, (testSelected / HostReceiptPolicy.owner).value),
    testQuick / testResultLogger := HostReceiptPolicy.logger((testQuick / testResultLogger).value, (testQuick / HostReceiptPolicy.owner).value),
    HostTaskBoundary.input(testOnly, testSelected / HostReceiptPolicy.owner),
    HostTaskBoundary.input(testSelected, testSelected / HostReceiptPolicy.owner),
    HostTaskBoundary.input(testQuick, testQuick / HostReceiptPolicy.owner),
    HostTaskBoundary.output(executeTests, executeTests / HostReceiptPolicy.owner),
    distageList := {
      val options = spaceDelimited("distage request options").parsed
      Def.uncached {
        val identities = DistageHostPolicy.arguments(distageBuildId.value, distageTargetId.value, distageCatalogueId.value)
        val arguments = DistageHostPolicy.inspectionArguments("list", identities, options, definedTests.value)
        val converter = fileConverter.value
        (run / runner).value.run(DistageHostPolicy.inspectionLauncher, fullClasspath.value.map(entry => converter.toPath(entry.data)), arguments, streams.value.log).get
      }
    },
    distagePlan := {
      val options = spaceDelimited("distage request options").parsed
      Def.uncached {
        val identities = DistageHostPolicy.arguments(distageBuildId.value, distageTargetId.value, distageCatalogueId.value)
        val arguments = DistageHostPolicy.inspectionArguments("plan", identities, options, definedTests.value)
        val converter = fileConverter.value
        (run / runner).value.run(DistageHostPolicy.inspectionLauncher, fullClasspath.value.map(entry => converter.toPath(entry.data)), arguments, streams.value.log).get
      }
    },
  )

  private def cacheableDigests(inherited: Map[String, Digest], definitions: Seq[TestDefinition], log: Logger): Map[String, Digest] = {
    val owned = HostReceiptPolicy.names(definitions).map(_.value)
    inherited.keys.filter(owned.contains).toVector.sorted.foreach { name =>
      log.info("DISTAGE_CACHE_PUBLICATION suite=" + name + " decision=omit reason=untracked-input-closure")
    }
    inherited.filterNot { case (name, _) => owned.contains(name) }
  }
}
