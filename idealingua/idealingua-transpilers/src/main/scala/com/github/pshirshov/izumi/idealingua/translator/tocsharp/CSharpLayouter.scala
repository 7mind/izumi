package com.github.pshirshov.izumi.idealingua.translator.tocsharp

import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.output.{Module, ModuleId}
import com.github.pshirshov.izumi.idealingua.model.publishing.BuildManifest.ManifestDependency
import com.github.pshirshov.izumi.idealingua.model.publishing.ProjectVersion
import com.github.pshirshov.izumi.idealingua.model.publishing.manifests.{CSharpBuildManifest, CSharpProjectLayout}
import com.github.pshirshov.izumi.idealingua.translator.CompilerOptions.CSharpTranslatorOptions
import com.github.pshirshov.izumi.idealingua.translator.{ExtendedModule, Layouted, Translated, TranslationLayouter}

// https://gist.github.com/davidfowl/ed7564297c61fe9ab814
// https://softwareengineering.stackexchange.com/questions/369504/directory-structure-for-a-net-solution
class CSharpLayouter(options: CSharpTranslatorOptions) extends TranslationLayouter {
  override def layout(outputs: Seq[Translated]): Layouted = {
    val layouted = options.manifest.layout match {
      case CSharpProjectLayout.NUGET =>
        buildNugetProject(outputs)

      case CSharpProjectLayout.PLAIN =>
        withRuntime(options, outputs)

    }
    Layouted(layouted)
  }

  private def buildNugetProject(outputs: Seq[Translated]): Seq[ExtendedModule] = {
    val rt = addPrefix(toRuntimeModules(options), Seq("src"))

    val sources = outputs.flatMap {
      t =>
        val did = t.typespace.domain.id
        val (testsSrcs, src) = t.modules
          .map(m => m.copy(id = m.id.copy(path = Seq.empty)))
          .map(m => ExtendedModule.DomainModule(did, m))
          .partition(_.module.meta.get("scope").contains("test"))

        val tests = if (testsSrcs.nonEmpty) {
          testsSrcs
        } else {
          Seq(ExtendedModule.RuntimeModule(Module(ModuleId(Seq.empty, ".keep"), "")))
        }

        val pid = projectId(did)
        val postfix = specPostfix(did)

        val pkgId = s"${options.manifest.nuget.id}-$postfix"
        val deps = t.typespace.domain.meta.directImports.map(i => ManifestDependency(s"${options.manifest.nuget.id}-${specPostfix(i.id)}", renderVersion(options.manifest.common.version)))
        val pkgMf = options.manifest.copy(nuget = options.manifest.nuget.copy(dependencies = deps.toList, id = pkgId))
        val nuspec = generateNuspec(pkgMf, List(s"src/$pid/**", s"tests/$pid/**"))
        val nuspecName = s"${CSharpLayouter.nuspecName(options.manifest, Some(s"-$postfix"))}"
        val nuspecModule = ExtendedModule.RuntimeModule(Module(ModuleId(Seq.empty, nuspecName), nuspec))

        addPrefix(src, Seq(s"src", pid)) ++
          addPrefix(tests, Seq(s"tests", pid)) ++
          Seq(nuspecModule)

    }


    val unifiedNuspec = generateNuspec(options.manifest, List("src/**", "tests/**"))
    val unifiedNuspecName = CSharpLayouter.nuspecName(options.manifest, None)
    val unifiedNuspecModule = ExtendedModule.RuntimeModule(Module(ModuleId(Seq.empty, unifiedNuspecName), unifiedNuspec))

    rt ++ sources ++ Seq(unifiedNuspecModule)
  }

  private def specPostfix(did: DomainId) = {
    baseProjectId(did, options.manifest.nuget.dropFQNSegments, options.manifest.nuget.projectIdPostfix.toSeq).mkString("-").toLowerCase
  }

  private def projectId(did: DomainId) = {
    baseProjectId(did, options.manifest.nuget.dropFQNSegments, options.manifest.nuget.projectIdPostfix.toSeq).map(_.capitalize).mkString("")
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
       |${manifest.nuget.dependencies.map(d => s"""            <dependency id="${d.module}" version="${d.version}" />""").mkString("\n")}
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
  protected[idealingua] def nuspecName(mf: CSharpBuildManifest, postfix: Option[String]): String = {
    val baseid = mf.nuget.id.replace(".", "-")
    val nuspecName = s"$baseid${postfix.getOrElse("")}.nuspec"
    nuspecName
  }

}
