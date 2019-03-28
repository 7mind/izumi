package com.github.pshirshov.izumi.idealingua.translator.toscala.layout

import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.publishing.ProjectNamingRule
import com.github.pshirshov.izumi.idealingua.translator.BaseNamingConvention

class ScalaNamingConvention(rule: ProjectNamingRule) {
  private val naming = new BaseNamingConvention(rule)

  def projectId(did: DomainId): String = {
    naming.baseProjectId(did).mkString("-").toLowerCase()
  }

  def pkgId: String = specialId(None)

  def bundleId: String = specialId(Some("bundle"))

  def specialId(what: Option[String]): String = {
    (what.toSeq ++ naming.baseProjectId(Seq.empty)).map(_.toLowerCase).mkString("-")
  }

}
