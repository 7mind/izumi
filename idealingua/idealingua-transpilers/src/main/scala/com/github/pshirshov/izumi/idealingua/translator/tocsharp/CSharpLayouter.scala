package com.github.pshirshov.izumi.idealingua.translator.tocsharp

import com.github.pshirshov.izumi.idealingua.model.output.{Module, ModuleId}
import com.github.pshirshov.izumi.idealingua.model.publishing.ProjectVersion
import com.github.pshirshov.izumi.idealingua.model.publishing.manifests.{CSharpBuildManifest, CSharpProjectLayout}
import com.github.pshirshov.izumi.idealingua.translator.CompilerOptions.CSharpTranslatorOptions
import com.github.pshirshov.izumi.idealingua.translator.{ExtendedModule, Layouted, Translated, TranslationLayouter}

class CSharpLayouter(options: CSharpTranslatorOptions) extends TranslationLayouter {
  override def layout(outputs: Seq[Translated]): Layouted = {
    val withRt = withRuntime(options, outputs)
    val layouted = options.manifest.layout match {
      case CSharpProjectLayout.NUGET =>
        buildNugetProject(withRt)

      case CSharpProjectLayout.PLAIN =>
        withRt

    }
    Layouted(layouted)
  }

  private def buildNugetProject(withRt: Seq[ExtendedModule]): Seq[ExtendedModule] = {
    // TODO: this is a dummy implementation, it does produce per-domain artifacts
    val nuspec = generateNuspec(options.manifest, List("src//**"))
    val nuspecName = CSharpLayouter.nuspecName(options.manifest)
    val nuspecModule = ExtendedModule.RuntimeModule(Module(ModuleId(Seq.empty, nuspecName), nuspec))
    addPrefix(withRt, Seq("src")) ++ Seq(nuspecModule)
  }

  private def generateNuspec(manifest: CSharpBuildManifest, filesFolder: List[String]): String = {
    // TODO: use safe xml builder
    s"""<?xml version="1.0"?>
       |<package >
       |    <metadata>
       |        <id>${manifest.nuget.id}</id>
       |        <version>${renderVersion(manifest.common.version)}</version>
       |        <authors>${manifest.common.publisher.name}</authors>
       |        <owners>${manifest.common.publisher.id}</owners>
       |        <licenseUrl>${manifest.common.licenses.head.url.url}</licenseUrl>
       |        <projectUrl>${manifest.common.website.url}</projectUrl>
       |        <iconUrl>${manifest.nuget.iconUrl}</iconUrl>
       |        <requireLicenseAcceptance>${manifest.nuget.requireLicenseAcceptance}</requireLicenseAcceptance>
       |        <releaseNotes>${manifest.common.releaseNotes}</releaseNotes>
       |        <description>${manifest.common.description}</description>
       |        <copyright>${manifest.common.copyright}</copyright>
       |        <tags>${manifest.common.tags.mkString(" ")}</tags>
       |        <dependencies>
       |        </dependencies>
       |     </metadata>
       |    <files>
       |${filesFolder.map(ff => s"""        <file src="$ff" target="build" />""").mkString("\n")}
       |    </files>
       |</package>
     """.stripMargin
  }

  private def renderVersion(version: ProjectVersion): String = {
    version.version
  }
}

object CSharpLayouter {
  protected[idealingua] def nuspecName(mf: CSharpBuildManifest): String = {
    val baseid = mf.nuget.id.replace(".", "-")
    val nuspecName = s"$baseid.nuspec"
    nuspecName
  }

}
