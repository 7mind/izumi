package com.github.pshirshov.izumi.idealingua.translator.tocsharp

import com.github.pshirshov.izumi.idealingua.model.publishing.ProjectVersion
import com.github.pshirshov.izumi.idealingua.model.publishing.manifests.CSharpBuildManifest
import com.github.pshirshov.izumi.idealingua.translator.CompilerOptions.CSharpTranslatorOptions
import com.github.pshirshov.izumi.idealingua.translator.{Layouted, Translated, TranslationLayouter}

class CSharpLayouter(options: CSharpTranslatorOptions) extends TranslationLayouter {
  override def layout(outputs: Seq[Translated]): Layouted = {
    Layouted(withRuntime(options, outputs))
  }

  // TODO: this is not in use for now
  def generateNuspec(manifest: CSharpBuildManifest, filesFolder: List[String] = List("csharp\\**")): String = {
    s"""<?xml version="1.0"?>
       |<package >
       |    <metadata>
       |        <id>${manifest.id}</id>
       |        <version>${renderVersion(manifest.common.version)}</version>
       |        <authors>${manifest.common.publisher.name}</authors>
       |        <owners>${manifest.common.publisher.id}</owners>
       |        <licenseUrl>${manifest.common.licenses.head.url.url}</licenseUrl>
       |        <projectUrl>${manifest.common.website.url}</projectUrl>
       |        <iconUrl>${manifest.iconUrl}</iconUrl>
       |        <requireLicenseAcceptance>${if (manifest.requireLicenseAcceptance) "true" else "false"}</requireLicenseAcceptance>
       |        <releaseNotes>${manifest.common.releaseNotes}</releaseNotes>
       |        <description>${manifest.common.description}</description>
       |        <copyright>${manifest.common.copyright}</copyright>
       |        <tags>${manifest.common.tags.mkString(" ")}</tags>
       |        <dependencies>
       |        </dependencies>
       |     </metadata>
       |    <files>
       |${filesFolder.map(ff => s"""        <file src="$ff" target="build" />""")}
       |    </files>
       |</package>
     """.stripMargin
  }

  private def renderVersion(version: ProjectVersion): String = {
    version.version
  }
}
