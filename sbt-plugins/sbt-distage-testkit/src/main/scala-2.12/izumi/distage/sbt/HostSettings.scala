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
    testQuick / testFilter := DistageHostPolicy.conservativeFilter(definedTests.value, (testQuick / testFilter).value, (testOnly / testFilter).value, streams.value.log),
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
