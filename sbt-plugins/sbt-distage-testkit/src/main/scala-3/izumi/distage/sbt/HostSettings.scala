package izumi.distage.sbt

import izumi.distage.sbt.DistageTestkitPlugin.autoImport.*

import sbt.*
import sbt.Keys.*
import sbt.complete.DefaultParsers.spaceDelimited

private[sbt] object HostSettings {
  def settings: Seq[Def.Setting[?]] = Seq(
    distageBuildId := thisProjectRef.value.build.toString,
    distageTargetId := thisProjectRef.value.project + "/" + configuration.value.name,
    testFrameworks ~= { frameworks => if (frameworks.contains(DistageHostPolicy.framework)) frameworks else frameworks :+ DistageHostPolicy.framework },
    distageCatalogueId := Def.uncached { DistageHostPolicy.catalogueId(definedTests.value) },
    testOptions := Def.uncached { DistageHostPolicy.withIdentity(testOptions.value, DistageHostPolicy.arguments(distageBuildId.value, distageTargetId.value, distageCatalogueId.value)) },
    testQuick / testFilter := Def.uncached { DistageHostPolicy.conservativeFilter(definedTests.value, (testQuick / testFilter).value, (testSelected / testFilter).value, streams.value.log) },
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
}
