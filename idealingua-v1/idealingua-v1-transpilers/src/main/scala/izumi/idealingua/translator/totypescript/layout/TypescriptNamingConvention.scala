package izumi.idealingua.translator.totypescript.layout

import izumi.idealingua.model.output.ModuleId
import izumi.idealingua.model.publishing.manifests.TypeScriptBuildManifest
import izumi.idealingua.translator.BaseNamingConvention

class TypescriptNamingConvention(manifest: TypeScriptBuildManifest) {
  private val naming = new BaseNamingConvention(manifest.yarn.projectNaming)

  def toDirName(parts: Seq[String]): String = {
    toDirParts(parts).mkString("-")
  }

  private def toDirParts(parts: Seq[String]): Seq[String] = {
    naming.baseProjectId(parts).map(_.toLowerCase)
  }

  def toScopedId(parts: Seq[String]): String = {
    s"${manifest.yarn.scope}/${toDirName(parts)}"
  }

  def makeName(m: ModuleId): String = {
    toDirName(m.path)
  }

  def specialId(what: String): String = {
    val parts = Seq(what) ++ toDirParts(Seq.empty)
    s"${manifest.yarn.scope}/${parts.mkString("-")}"
  }

  def irtDependency: String = specialId("irt")

  def bundleId: String = specialId("bundle")

}
