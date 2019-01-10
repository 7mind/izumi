package com.github.pshirshov.izumi.idealingua.translator.tocsharp.layout

import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
import com.github.pshirshov.izumi.idealingua.model.output.{Module, ModuleId}
import com.github.pshirshov.izumi.idealingua.model.publishing.BuildManifest.ManifestDependency
import com.github.pshirshov.izumi.idealingua.model.publishing.manifests.{CSharpBuildManifest, CSharpProjectLayout}
import com.github.pshirshov.izumi.idealingua.translator.CompilerOptions.CSharpTranslatorOptions
import com.github.pshirshov.izumi.idealingua.translator._


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
  * Source-only packages:
  *  - https://stackoverflow.com/questions/52880687/how-to-share-source-code-via-nuget-packages-for-use-in-net-core-projects
  */
class CSharpLayouter(options: CSharpTranslatorOptions) extends TranslationLayouter {
  private val naming = new CSharpNamingConvention(options.manifest.nuget.projectNaming)

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
        val (testsSrcs, mainSrcs) = t.modules
          .map(m => m.copy(id = m.id.copy(path = Seq.empty)))
          .map(m => ExtendedModule.DomainModule(did, m))
          .partition(_.module.meta.get("scope").contains("test"))

        val src = ensureNotEmpty(mainSrcs)
        val tests = ensureNotEmpty(testsSrcs)

        val deps = t.typespace.domain.meta.directImports.map(i => ManifestDependency(naming.projectId(i.id), mfVersion))
        val pkgMf = options.manifest.copy(nuget = options.manifest.nuget.copy(dependencies = options.manifest.nuget.dependencies ++ deps.toList ++ Seq(ManifestDependency(naming.irtId, mfVersion))))

        val pid = naming.projectDirName(did)
        val nuspecModule = mkNuspecModule(List(s"src/$pid/**"), withTests = false, naming.projectId(did), pkgMf)

        val pkgMfTest = pkgMf.copy(nuget = pkgMf.nuget.copy(dependencies = pkgMf.nuget.dependencies ++ Seq(ManifestDependency(naming.projectId(t.typespace.domain.id), mfVersion))))
        val nuspecTestModule = mkNuspecModule(List(s"tests/$pid/**"), withTests = true, naming.testProjectId(did), pkgMfTest)

        addPrefix(src, Seq(s"src", pid)) ++
          addPrefix(tests, Seq(s"tests", pid)) ++
          Seq(nuspecModule, nuspecTestModule)

    }

    val everythingNuspecModule = mkBundle(outputs, naming.pkgId)

    val everythingNuspecModuleTest = mkTestBundle(outputs, naming.pkgId, naming.pkgIdTest)

    val unifiedNuspecModule = mkNuspecModule(List("src/**", "tests/**"), withTests = true, naming.bundleId, options.manifest)
    val irtModule = mkNuspecModule(List("src/IRT/**"), withTests = false, naming.irtId, options.manifest)


    val allIds = Seq(
      naming.irtId,
      naming.pkgId,
    ) ++ outputs.map {
      t =>
        naming.projectId(t.typespace.domain.id)
    }


    val packagesConfig = mkPackagesConfig(allIds)

    rt ++ sources ++ Seq(unifiedNuspecModule, everythingNuspecModule, everythingNuspecModuleTest, irtModule) ++ packagesConfig
  }


  private def mkPackagesConfig(allIds: Seq[String]): Seq[ExtendedModule.RuntimeModule] = {
    val pkgConfigEntries = allIds.map(id => s"""<package id="$id" version="$mfVersion" />""")
    val packagesConfig =
      s"""<?xml version="1.0" encoding="utf-8"?>
         |<packages>
         |${pkgConfigEntries.mkString("\n").shift(2)}
         |</packages>
      """.stripMargin

    val projectEntries = allIds.map {
      id =>
        s"""<PackageReference Include="$id" Version="$mfVersion">
           |  <PrivateAssets>all</PrivateAssets>
           |</PackageReference>""".stripMargin
    }

    val importsProject =
      s"""<?xml version="1.0" encoding="utf-8"?>
         |<Project ToolsVersion="4.0" DefaultTargets="Build" xmlns="http://schemas.microsoft.com/developer/msbuild/2003">
         |    <ItemGroup>
         |${projectEntries.mkString("\n").shift(8)}
         |    </ItemGroup>
         |</Project>""".stripMargin

    Seq(
      ExtendedModule.RuntimeModule(Module(ModuleId(Seq.empty, "packages.config"), packagesConfig, Map.empty)),
      ExtendedModule.RuntimeModule(Module(ModuleId(Seq.empty, "references.csproj.import"), importsProject, Map.empty)),
    )
  }

  private def mkBundle(outputs: Seq[Translated], pkgId: String): ExtendedModule = {
    val allModules = outputs.map {
      t =>
        ManifestDependency(naming.projectId(t.typespace.domain.id), mfVersion)
    }
    val pkgMf = options.manifest.copy(nuget = options.manifest.nuget.copy(dependencies = options.manifest.nuget.dependencies ++ allModules.toList))
    val everythingNuspecModule = mkNuspecModule(List.empty, withTests = false, pkgId, pkgMf)
    everythingNuspecModule
  }

  private def mkTestBundle(outputs: Seq[Translated], pkgId: String, pkgIdTest: String): ExtendedModule = {
    val allModulesTest = Seq(ManifestDependency(pkgId, mfVersion)) ++ outputs.map {
      t =>
        ManifestDependency(naming.testProjectId(t.typespace.domain.id), mfVersion)
    }
    val pkgMfTest = options.manifest.copy(nuget = options.manifest.nuget.copy(dependencies = options.manifest.nuget.dependencies ++ allModulesTest.toList))
    val everythingNuspecModuleTest = mkNuspecModule(List.empty, withTests = true, pkgIdTest, pkgMfTest)
    everythingNuspecModuleTest
  }

  private def mfVersion: String = {
    renderVersion(options.manifest.common.version)
  }


  private def mkNuspecModule(files: List[String], withTests: Boolean, id: String, mf0: CSharpBuildManifest): ExtendedModule = {
    val unifiedNuspec = generateNuspec(mf0, id, files, withTests)
    val unifiedNuspecName = naming.nuspecName(id)
    val unifiedNuspecModule = ExtendedModule.RuntimeModule(Module(ModuleId(Seq.empty, unifiedNuspecName), unifiedNuspec))
    unifiedNuspecModule
  }

  private def ensureNotEmpty(sources: Seq[ExtendedModule.DomainModule]): Seq[ExtendedModule] = {
    if (sources.nonEmpty) {
      sources
    } else {
      Seq(ExtendedModule.RuntimeModule(Module(ModuleId(Seq.empty, ".keep"), "")))
    }
  }

  private def generateNuspec(manifest: CSharpBuildManifest, id: String, filesFolder: List[String], withTests: Boolean): String = {
    val allDependencies = if (withTests) {
      manifest.nuget.dependencies ++ manifest.nuget.testDependencies
    } else {
      manifest.nuget.dependencies
    }

    // TODO: use safe xml builder
    s"""<?xml version="1.0"?>
       |<package >
       |    <metadata>
       |        <id>$id</id>
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
       |          <files include="**/*.cs" buildAction="Compile" copyToOutput="false" />
       |        </contentFiles>
       |     </metadata>
       |    <files>
       |${filesFolder.map(ff => s"""        <file src="$ff" target="contentFiles/any/any/src" />""").mkString("\n")}
       |    </files>
       |</package>
     """.stripMargin
  }
}

