package com.github.pshirshov.izumi.idealingua.translator.tocsharp

import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.output.{Module, ModuleId}
import com.github.pshirshov.izumi.idealingua.model.publishing.BuildManifest.ManifestDependency
import com.github.pshirshov.izumi.idealingua.model.publishing.manifests.{CSharpBuildManifest, CSharpProjectLayout}
import com.github.pshirshov.izumi.idealingua.translator.CompilerOptions.CSharpTranslatorOptions
import com.github.pshirshov.izumi.idealingua.translator.{ExtendedModule, Layouted, Translated, TranslationLayouter}


/**
  * Directory structure:
  *  - https://gist.github.com/davidfowl/ed7564297c61fe9ab814
  *  - https://softwareengineering.stackexchange.com/questions/369504/directory-structure-for-a-net-solution
  *
  * Naming convention:
  *  - https://docs.microsoft.com/en-us/nuget/create-packages/creating-a-package#choosing-a-unique-package-identifier-and-setting-the-version-number
  *
  * Manifest options:
  *  - https://docs.microsoft.com/en-us/nuget/reference/nuspec
  *  - https://docs.microsoft.com/en-us/nuget/reference/package-versioning
  *
  *  Source-only packages:
  *  - https://stackoverflow.com/questions/52880687/how-to-share-source-code-via-nuget-packages-for-use-in-net-core-projects
  */
class CSharpLayouter(options: CSharpTranslatorOptions) extends TranslationLayouter {
  private final val testSuffix = "Tests"

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
    val irtId = s"${options.manifest.nuget.id}-IRT"

    val sources = outputs.flatMap {
      t =>
        val did = t.typespace.domain.id
        val (testsSrcs, mainSrcs) = t.modules
          .map(m => m.copy(id = m.id.copy(path = Seq.empty)))
          .map(m => ExtendedModule.DomainModule(did, m))
          .partition(_.module.meta.get("scope").contains("test"))

        val src = ensureNotEmpty(mainSrcs)
        val tests = ensureNotEmpty(testsSrcs)

        val deps = t.typespace.domain.meta.directImports.map(i => ManifestDependency(projectId(i.id), mfVersion))
        val pkgMf = options.manifest.copy(nuget = options.manifest.nuget.copy(dependencies = options.manifest.nuget.dependencies ++ deps.toList ++ Seq(ManifestDependency(irtId, mfVersion))))

        val pid = projectDirName(did)
        val nuspecModule = mkNuspecModule(List(s"src/$pid/**"), withTests = false, projectId(did), pkgMf)

        val pkgMfTest = pkgMf.copy(nuget = pkgMf.nuget.copy(dependencies = pkgMf.nuget.dependencies ++ Seq(ManifestDependency(projectId(t.typespace.domain.id), mfVersion))))
        val nuspecTestModule = mkNuspecModule(List(s"tests/$pid/**"), withTests = true, testProjectId(did), pkgMfTest)

        addPrefix(src, Seq(s"src", pid)) ++
          addPrefix(tests, Seq(s"tests", pid)) ++
          Seq(nuspecModule, nuspecTestModule)

    }

    val everythingNuspecModule = mkBundle(outputs)

    val everythingNuspecModuleTest = mkTestBundle(outputs)

    val unifiedNuspecModule = mkNuspecModule(List("src/**", "tests/**"), withTests = true, s"${options.manifest.nuget.id}-Bundle", options.manifest)
    val irtModule = mkNuspecModule(List("src/IRT/**"), withTests = false, irtId, options.manifest)

    rt ++ sources ++ Seq(unifiedNuspecModule, everythingNuspecModule, everythingNuspecModuleTest, irtModule)
  }



  private def mkBundle(outputs: Seq[Translated]): ExtendedModule = {
    val allModules = outputs.map {
      t =>
        ManifestDependency(projectId(t.typespace.domain.id), mfVersion)
    }
    val pkgId = s"${options.manifest.nuget.id}"
    val pkgMf = options.manifest.copy(nuget = options.manifest.nuget.copy(dependencies = options.manifest.nuget.dependencies ++ allModules.toList))
    val everythingNuspecModule = mkNuspecModule(List.empty, withTests = false, pkgId, pkgMf)
    everythingNuspecModule
  }

  private def mkTestBundle(outputs: Seq[Translated]): ExtendedModule = {
    val allModulesTest = Seq(ManifestDependency(s"${options.manifest.nuget.id}", mfVersion)) ++ outputs.map {
      t =>
        ManifestDependency(testProjectId(t.typespace.domain.id), mfVersion)
    }
    val pkgIdTest = s"${options.manifest.nuget.id}-$testSuffix"
    val pkgMfTest = options.manifest.copy(nuget = options.manifest.nuget.copy(dependencies = options.manifest.nuget.dependencies ++ allModulesTest.toList))
    val everythingNuspecModuleTest = mkNuspecModule(List.empty, withTests = true, pkgIdTest, pkgMfTest)
    everythingNuspecModuleTest
  }

  private def mfVersion: String = {
    renderVersion(options.manifest.common.version)
  }

  private def mkNuspecModule(files: List[String], withTests: Boolean, id: String, mf0: CSharpBuildManifest): ExtendedModule = {
    val mf = mf0.copy(nuget = mf0.nuget.copy(id = id))
    val unifiedNuspec = generateNuspec(mf, files, withTests)
    val unifiedNuspecName = CSharpLayouter.nuspecName(mf)
    val unifiedNuspecModule = ExtendedModule.RuntimeModule(Module(ModuleId(Seq.empty, unifiedNuspecName), unifiedNuspec))
    unifiedNuspecModule
  }

  private def projectId(did: DomainId): String = {
    val pid = baseProjectId(did, options.manifest.nuget.dropFQNSegments, options.manifest.nuget.projectIdPostfix.toSeq).map(_.capitalize).mkString(".")
    s"${options.manifest.nuget.id}.$pid"
  }

  private def testProjectId(did: DomainId): String = {
    s"${projectId(did)}-$testSuffix"
  }

  private def projectDirName(did: DomainId): String = {
    baseProjectId(did, options.manifest.nuget.dropFQNSegments, options.manifest.nuget.projectIdPostfix.toSeq).map(_.capitalize).mkString("")
  }

  private def ensureNotEmpty(sources: Seq[ExtendedModule.DomainModule]): Seq[ExtendedModule] = {
    if (sources.nonEmpty) {
      sources
    } else {
      Seq(ExtendedModule.RuntimeModule(Module(ModuleId(Seq.empty, ".keep"), "")))
    }
  }

  private def generateNuspec(manifest: CSharpBuildManifest, filesFolder: List[String], withTests: Boolean): String = {
    val allDependencies = if (withTests) {
      manifest.nuget.dependencies ++ manifest.nuget.testDependencies
    } else {
      manifest.nuget.dependencies
    }

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
       |${allDependencies.map(d => s"""            <dependency id="${d.module}" version="${d.version}" />""").mkString("\n")}
       |        </dependencies>
       |        <contentFiles>
       |${filesFolder.map(ff => s"""        <files include="**/*.cs" buildAction="Compile" copyToOutput="true" />""").mkString("\n")}
       |        </contentFiles>
       |     </metadata>
       |    <files>
       |${filesFolder.map(ff => s"""        <file src="$ff" target="contentFiles/any/any/src" />""").mkString("\n")}
       |    </files>
       |</package>
     """.stripMargin
  }
}

object CSharpLayouter {
  protected[idealingua] def nuspecName(mf: CSharpBuildManifest): String = {
    val baseid = mf.nuget.id.split('.').map(_.capitalize).mkString(".")
    val nuspecName = s"$baseid.nuspec"
    nuspecName
  }

}
