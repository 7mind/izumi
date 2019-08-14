package izumi.idealingua.translator.tocsharp.layout

import izumi.idealingua.model.common.DomainId
import izumi.idealingua.model.publishing.ProjectNamingRule
import izumi.idealingua.translator.BaseNamingConvention

class CSharpNamingConvention(rule: ProjectNamingRule) {
  private val naming = new BaseNamingConvention(rule)
  private final val testSuffix = "Tests"

  def irtId: String = specialId(Some("IRT"))

  def irtDir: String = specialDirName(Some("IRT"))

  def pkgId: String = specialId(None)

  def pkgIdTest: String = specialId(Some(testSuffix))

  def bundleId: String = specialId(Some("Bundle"))

  def nuspecName(id: String): String = {
    val nuspecName = s"$id.nuspec"
    nuspecName
  }

  def specialId(what: Option[String]): String = {
    (what.toSeq ++ naming.baseProjectId(Seq.empty)).map(_.capitalize).mkString(".")
  }

  def specialDirName(what: Option[String]): String = {
    (what.toSeq ++ naming.baseProjectId(Seq.empty)).map(_.capitalize).mkString
  }

  def projectId(did: DomainId): String = {
    naming.baseProjectId(did).map(_.capitalize).mkString(".")
  }

  def testProjectId(did: DomainId): String = {
    s"${projectId(did)}-$testSuffix"
  }

  def projectDirName(did: DomainId): String = {
    naming.baseProjectId(did).map(_.capitalize).mkString
  }

}
