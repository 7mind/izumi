import sbt.{CrossVersion, ModuleID}
import scoverage.ScoverageSbtPlugin
import java.io.File

object ScoverageCompilerDependencies {
  private val compilerPlugin = "scalac-scoverage-plugin"
  private val compilerArtifacts = Set(compilerPlugin, "scalac-scoverage-domain", "scalac-scoverage-reporter", "scalac-scoverage-serializer")

  def forPlatform(dependencies: Seq[ModuleID], scalaVersion: String, scalaBinaryVersion: String): Seq[ModuleID] = {
    val compilerDependencies = dependencies.map { dependency =>
      if (dependency.organization == "org.scoverage" && compilerArtifacts.contains(dependency.name)) {
        val suffix = if (dependency.name == compilerPlugin) scalaVersion else scalaBinaryVersion
        dependency.withName(dependency.name + "_" + suffix).cross(CrossVersion.disabled).withConfigurations(Some(ScoverageSbtPlugin.ScoveragePluginConfig.name))
      } else dependency
    }
    val macroRuntime = dependencies.find(dependency => dependency.organization == "org.scoverage" && dependency.name == compilerPlugin).toSeq.map { plugin =>
      ModuleID("org.scoverage", "scalac-scoverage-runtime_" + scalaBinaryVersion, plugin.revision)
        .cross(CrossVersion.disabled).withConfigurations(Some(ScoverageSbtPlugin.ScoveragePluginConfig.name))
    }
    compilerDependencies ++ macroRuntime
  }

  def macroClasspath(dependencies: Seq[File], compilerDependencies: Seq[File], scalaBinaryVersion: String): String = {
    val hostRuntime = compilerDependencies.filter(_.getName.startsWith("scalac-scoverage-runtime_" + scalaBinaryVersion + "-"))
    require(hostRuntime.size == 1, "Instrumented macros require exactly one JVM Scoverage runtime")
    val macroDependencies = dependencies.filterNot(_.getName.startsWith("scalac-scoverage-runtime"))
    (hostRuntime ++ macroDependencies).distinct.map(_.getAbsolutePath).mkString(File.pathSeparator)
  }
}
