package com.github.pshirshov.izumi.idealingua.translator.totypescript.layout

import com.github.pshirshov.izumi.idealingua.model.output.ModuleId
import com.github.pshirshov.izumi.idealingua.model.publishing.manifests.TypeScriptBuildManifest
import com.github.pshirshov.izumi.idealingua.translator.BaseNamingConvention

class TypescriptNamingConvention(manifest: TypeScriptBuildManifest) {
  private val naming = new BaseNamingConvention(manifest.yarn.projectNaming)

  def toDirName(parts: Seq[String]): String = {
    naming.baseProjectId(parts).map(_.toLowerCase).mkString("-")
  }

  def toScopedId(parts: Seq[String]): String = {
    s"${manifest.yarn.scope}/${toDirName(parts)}"
  }

  def makeName(m: ModuleId): String = {
    toDirName(m.path)
  }

  def specialId(what: String): String = {
    s"${manifest.yarn.scope}/$what-${toDirName(Seq.empty)}"
  }

  def irtDependency: String = specialId("irt")

  def bundleId: String = specialId("bundle")

}
