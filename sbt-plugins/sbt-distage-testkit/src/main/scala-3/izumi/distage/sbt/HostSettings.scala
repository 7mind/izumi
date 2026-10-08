package izumi.distage.sbt

import izumi.distage.sbt.DistageTestkitPlugin.autoImport.*

import sbt.*
import sbt.Keys.*
import sbt.complete.DefaultParsers.spaceDelimited
import sbt.util.{ActionCache, Digest, Logger}
import sbt.util.CacheImplicits.given

private[sbt] object HostSettings {
  def globalSettings: Seq[Def.Setting[?]] = Seq(
    Global / commandProgress := Seq(new HostCommandCompletion(
      ExecuteProgress2.aggregate(commandProgress.value),
      HostReceiptPolicy.owner.?.all(ScopeFilter(inAnyProject, inAnyConfiguration, inTasks(testSelected, testQuick, executeTests))).value.flatten.distinct,
    )),
  )

  def settings: Seq[Def.Setting[?]] = Seq(
    distageBuildId := thisProjectRef.value.build.toString,
    distageTargetId := thisProjectRef.value.project + "/" + configuration.value.name,
    distageEventDirectory := target.value / "distage-events",
    HostJUnitReports.format := Def.uncached { HostJUnitFileFormat.configured(sys.props.get(HostJUnitFileFormat.Property), sys.env.get(HostJUnitFileFormat.Environment)) },
    testFrameworks ~= { frameworks => if (frameworks.contains(DistageHostPolicy.framework)) frameworks else frameworks :+ DistageHostPolicy.framework },
    loadedTestFrameworks := Def.uncached { loadedTestFrameworks.value.map { case (key, framework) => key -> HostCompletionFramework.wrap(framework) } },
    distageCatalogueId := Def.uncached { DistageHostPolicy.catalogueId(definedTests.value) },
    testOptions := Def.uncached { DistageHostPolicy.withIdentity(DistageHostPolicy.withEvents(testOptions.value, distageEventDirectory.value), DistageHostPolicy.arguments(distageBuildId.value, distageTargetId.value, distageCatalogueId.value)) },
    testSelected / HostReceiptPolicy.owner := HostReceiptPolicy.ownerAt(target.value),
    testQuick / HostReceiptPolicy.owner := HostReceiptPolicy.ownerAt(target.value),
    executeTests / HostReceiptPolicy.owner := HostReceiptPolicy.ownerAt(target.value),
    testSelected / testGrouping := Def.uncached { (testSelected / testGrouping).value.map((testSelected / HostReceiptPolicy.owner).value.completion.group) },
    testQuick / testGrouping := Def.uncached { (testQuick / testGrouping).value.map((testQuick / HostReceiptPolicy.owner).value.completion.group) },
    test / testGrouping := Def.uncached { (test / testGrouping).value.map((executeTests / HostReceiptPolicy.owner).value.completion.group) },
    testSelected / definedTestDigests := Def.uncached { cacheableDigests((testSelected / definedTestDigests).value, definedTests.value, streams.value.log) },
    testQuick / definedTestDigests := Def.uncached { cacheableDigests((testQuick / definedTestDigests).value, definedTests.value, streams.value.log) },
    test / definedTestDigests := Def.uncached { cacheableDigests((test / definedTestDigests).value, definedTests.value, streams.value.log) },
    testSelected / HostReceiptPolicy.normalisedFilter := Def.uncached {
      val filter = new HostSelectionObserver((testSelected / testFilter).value, HostReceiptPolicy.names(definedTests.value), (testSelected / HostReceiptPolicy.owner).value)
      new HostReportedSelection(filter, (name, arguments) => {
        if (HostSelectionPolicy.request(arguments)(name)) HostSelectionPolicy.Reason.InheritedFilter else HostSelectionPolicy.Reason.UserRequest
      }, streams.value.log)
    },
    testQuick / testFilter := Def.uncached {
      DistageHostPolicy.conservativeFilter(definedTests.value, (testQuick / testFilter).value, (testSelected / testFilter).value, streams.value.log)
    },
    testQuick / HostReceiptPolicy.normalisedFilter := Def.uncached {
      val digests = (testQuick / definedTestDigests).value
      val cache = Def.cacheConfiguration.value
      val owned = HostReceiptPolicy.names(definedTests.value)
      // SBT2.0.9 keys successful suite actions by framework options and suite digest.
      val cachedSuccess = (name: String, options: Seq[String]) => digests.get(name).exists(digest => ActionCache.exists(options, digest, Digest.zero, cache))
      val filter = new HostSelectionObserver((testQuick / testFilter).value, owned, (testQuick / HostReceiptPolicy.owner).value)
      new HostReportedSelection(filter, (name, arguments) => {
        if (!HostSelectionPolicy.request(arguments)(name)) HostSelectionPolicy.Reason.UserRequest
        else if (!owned.contains(HostSuiteName(name)) && cachedSuccess(name, arguments.dropWhile(_ != "--").drop(1))) HostSelectionPolicy.Reason.CachedSuccess
        else HostSelectionPolicy.Reason.InheritedFilter
      }, streams.value.log)
    },
    testSelected / testExecution := Def.uncached {
      HostSelectionPolicy.configured(HostReceiptPolicy.execution((testSelected / testExecution).value, definedTests.value, loadedTestFrameworks.value.keys.toVector, (testSelected / HostReceiptPolicy.owner).value, full = false, HostJUnitReports.format.value), definedTests.value, streams.value.log)
    },
    testQuick / testExecution := Def.uncached {
      HostSelectionPolicy.configured(HostReceiptPolicy.execution((testQuick / testExecution).value, definedTests.value, loadedTestFrameworks.value.keys.toVector, (testQuick / HostReceiptPolicy.owner).value, full = false, HostJUnitReports.format.value), definedTests.value, streams.value.log)
    },
    test / testExecution := Def.uncached {
      HostSelectionPolicy.configured(HostReceiptPolicy.execution((test / testExecution).value, definedTests.value, loadedTestFrameworks.value.keys.toVector, (executeTests / HostReceiptPolicy.owner).value, full = true, HostJUnitReports.format.value), definedTests.value, streams.value.log)
    },
    testSelected / testResultLogger := HostReceiptPolicy.logger((testSelected / testResultLogger).value, (testSelected / HostReceiptPolicy.owner).value),
    testQuick / testResultLogger := HostReceiptPolicy.logger((testQuick / testResultLogger).value, (testQuick / HostReceiptPolicy.owner).value),
    testSelected / HostReceiptPolicy.normalisedLogger := HostReceiptPolicy.logger((testSelected / testResultLogger).value, (testSelected / HostReceiptPolicy.owner).value),
    testQuick / HostReceiptPolicy.normalisedLogger := HostReceiptPolicy.logger((testQuick / testResultLogger).value, (testQuick / HostReceiptPolicy.owner).value),
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
