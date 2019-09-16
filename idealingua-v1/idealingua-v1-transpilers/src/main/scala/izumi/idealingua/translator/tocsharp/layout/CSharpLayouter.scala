package izumi.idealingua.translator.tocsharp.layout

import java.util.UUID

import izumi.fundamentals.platform.strings.IzString._
import izumi.idealingua.model.output.{Module, ModuleId}
import izumi.idealingua.model.publishing.BuildManifest.ManifestDependency
import izumi.idealingua.model.publishing.manifests.{CSharpBuildManifest, CSharpProjectLayout}
import izumi.idealingua.translator.CompilerOptions.CSharpTranslatorOptions
import izumi.idealingua.translator._

import scala.xml.Elem


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
  private val p = new scala.xml.PrettyPrinter(120, 4)

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
    val basicDeps = options.manifest.nuget.dependencies
    val basicTestDeps = basicDeps ++ options.manifest.nuget.testDependencies
    val rt = addPrefix(toRuntimeModules(options) ++ csproj(naming.irtDir, Seq.empty, basicDeps), Seq("src", naming.irtDir))


    val sources = outputs.flatMap {
      t =>
        val did = t.typespace.domain.id
        val (testsSrcs, mainSrcs) = t.modules
          .map(m => m.copy(id = m.id.copy(path = Seq.empty)))
          .map(m => ExtendedModule.DomainModule(did, m))
          .partition(_.module.meta.get("scope").contains("test"))

        val deps = t.typespace.domain.meta.directImports.map(i => ManifestDependency(naming.projectId(i.id), mfVersion))
        val pkgMf = options.manifest.copy(nuget = options.manifest.nuget.copy(dependencies = options.manifest.nuget.dependencies ++ deps.toList ++ Seq(ManifestDependency(naming.irtId, mfVersion))))

        val prjDir = naming.projectDirName(did)

        val prjId = naming.projectId(did)
        val nuspecModule = mkNuspecModule(List(s"../src/$prjDir/**/*.cs"), deps, prjId, pkgMf)
        val csdeps = t.typespace.domain.meta.directImports.map {
          i =>
            val id = i.id
            val prjDirName = s"${naming.projectDirName(id)}"
            val prjName = prjDirName
            s"src/$prjDirName/$prjName.csproj"
        } ++ Seq(s"src/${naming.irtDir}/${naming.irtDir}.csproj")

        val src = mainSrcs ++ csproj(prjDir, csdeps, basicDeps)

        val testDeps = pkgMf.nuget.dependencies ++ pkgMf.nuget.testDependencies ++ Seq(ManifestDependency(naming.projectId(t.typespace.domain.id), mfVersion))
        val pkgMfTest = pkgMf.copy(nuget = pkgMf.nuget.copy(dependencies = testDeps))
        val prjIdTest = naming.testProjectId(did)
        val nuspecTestModule = mkNuspecModule(List(s"../tests/$prjDir/**/*.cs"), testDeps, prjIdTest, pkgMfTest)

        val csdepsTest = t.typespace.domain.meta.directImports.map {
          i =>
            val id = i.id
            val prjDirName = s"${naming.projectDirName(id)}"
            val prjTestName = s"${naming.projectDirName(id)}.Test"

            s"tests/$prjDirName/$prjTestName.csproj"
        }


        val tests = testsSrcs ++ csproj(s"$prjDir.Test", csdepsTest ++ Seq(s"src/$prjDir/$prjDir.csproj"), basicTestDeps)

        val nuspecs = Seq(nuspecModule, nuspecTestModule)
        addPrefix(src, Seq(s"src", prjDir)) ++
          addPrefix(tests, Seq(s"tests", prjDir)) ++
          addPrefix(nuspecs, Seq("nuspec"))
    }


    val nuspecs = mkNuspecs(outputs, basicDeps, basicTestDeps)
    val packagesConfig = makeExamples(outputs)
    val solution = generateSolution(outputs)

    rt ++
      solution ++
      sources ++
      nuspecs ++
      packagesConfig
  }


  private def mkNuspecs(outputs: Seq[Translated], basicDeps: List[ManifestDependency], basicTestDeps: List[ManifestDependency]): Seq[ExtendedModule] = {
    val everythingNuspecModule = mkBundle(outputs, naming.pkgId)
    val everythingNuspecModuleTest = mkTestBundle(outputs, naming.pkgId, naming.pkgIdTest)

    val unifiedNuspecModule = mkNuspecModule(List("../src/**/*.cs", "../tests/**/*.cs"), basicTestDeps, naming.bundleId, options.manifest)
    val irtModule = mkNuspecModule(List(s"../src/${naming.irtDir}/**/*.cs"), basicDeps, naming.irtDir, options.manifest)
    val bundleNuspecs = Seq(everythingNuspecModule, everythingNuspecModuleTest) ++ Seq(unifiedNuspecModule)
    addPrefix(bundleNuspecs ++ Seq(irtModule), Seq("nuspec"))
  }

  private def makeExamples(outputs: Seq[Translated]): Seq[ExtendedModule] = {
    val allIds = Seq(
      naming.irtId,
      naming.pkgId,
    ) ++ outputs.map {
      t =>
        naming.projectId(t.typespace.domain.id)
    }


    val packagesConfig = mkPackagesConfig(allIds)
    addPrefix(packagesConfig, Seq("samples"))
  }

  private def generateSolution(outputs: Seq[Translated]): Seq[ExtendedModule.RuntimeModule] = {
    val projects = outputs.flatMap {
      out =>
        val id = out.typespace.domain.id

        val prjDirName = s"${naming.projectDirName(id)}"
        val prjName = prjDirName
        val prjTestName = s"$prjDirName.Test"

        Seq(
          CSProj(naming.projectId(id), s"src/$prjDirName/$prjName.csproj", isTest = false),
          CSProj(naming.testProjectId(id), s"tests/$prjDirName/$prjTestName.csproj", isTest = true),
        )
    } ++ Seq(CSProj(naming.irtDir, s"src/${naming.irtDir}/${naming.irtDir}.csproj", isTest = false))
    val sln = solution(projects)
    sln
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
    val deps = options.manifest.nuget.dependencies ++ allModules.toList
    val everythingNuspecModule = mkNuspecModule(List.empty, deps, pkgId, options.manifest)
    everythingNuspecModule
  }

  private def mkTestBundle(outputs: Seq[Translated], pkgId: String, pkgIdTest: String): ExtendedModule = {
    val allModulesTest = Seq(ManifestDependency(pkgId, mfVersion)) ++ outputs.map {
      t =>
        ManifestDependency(naming.testProjectId(t.typespace.domain.id), mfVersion)
    }
    val testDeps = options.manifest.nuget.dependencies ++ options.manifest.nuget.testDependencies ++ allModulesTest.toList
    val everythingNuspecModuleTest = mkNuspecModule(List.empty, testDeps, pkgIdTest, options.manifest)
    everythingNuspecModuleTest
  }

  private def mfVersion: String = {
    renderVersion(options.manifest.common.version)
  }


  private def mkNuspecModule(files: List[String], deps: Seq[ManifestDependency], id: String, mf0: CSharpBuildManifest): ExtendedModule = {
    val unifiedNuspec = generateNuspec(mf0, id, files, deps)
    val unifiedNuspecName = naming.nuspecName(id)
    val unifiedNuspecModule = ExtendedModule.RuntimeModule(Module(ModuleId(Seq.empty, unifiedNuspecName), unifiedNuspec))
    unifiedNuspecModule
  }

  case class CSProj(name: String, path: String, isTest: Boolean)

  case class CSProjEx(name: String, path: String, uid: String, folderId: String)

  case class CSFolder(name: String, path: String, folderId: String)

  implicit class StringEx(s: Seq[String]) {
    def slnShift(size: Int): String = {
      s.mkString("\n").split('\n').map(l => s"${"\t" * size}$l").mkString("\n")
    }
  }

  private def solution(projects: Seq[CSProj]): Seq[ExtendedModule.RuntimeModule] = {
    val csProjectType = "{FAE04EC0-301F-11D3-BF4B-00C04F79EFBC}" //uid()
    val folderProjectType = "{2150E333-8FDC-42A3-9474-1A3956D46DE8}"
    val testId = uid()
    val srcId = uid()
    val exProjects = projects.map {
      p =>
        val folder = if (p.isTest) {
          testId
        } else {
          srcId
        }
        CSProjEx(p.name, p.path, uid(), folder)
    }
    val projectConfigs = exProjects.map {
      p =>
        s"""${p.uid}.Debug|Any CPU.ActiveCfg = Debug|Any CPU
           |${p.uid}.Debug|Any CPU.Build.0 = Debug|Any CPU
           |${p.uid}.Debug|x64.ActiveCfg = Debug|Any CPU
           |${p.uid}.Debug|x64.Build.0 = Debug|Any CPU
           |${p.uid}.Debug|x86.ActiveCfg = Debug|Any CPU
           |${p.uid}.Debug|x86.Build.0 = Debug|Any CPU
           |${p.uid}.Release|Any CPU.ActiveCfg = Release|Any CPU
           |${p.uid}.Release|Any CPU.Build.0 = Release|Any CPU
           |${p.uid}.Release|x64.ActiveCfg = Release|Any CPU
           |${p.uid}.Release|x64.Build.0 = Release|Any CPU
           |${p.uid}.Release|x86.ActiveCfg = Release|Any CPU
           |${p.uid}.Release|x86.Build.0 = Release|Any CPU""".stripMargin
    }.slnShift(2)

    val folders = exProjects.map {
      p =>
        s"${p.uid} = ${p.folderId}"
    }.slnShift(2)

    val projectDefs = exProjects.map {
      p =>
        s"""Project("$csProjectType") = "${p.name}", "${p.path.replace('/', '\\')}", "${p.uid}"
           |EndProject""".stripMargin
    }.mkString("\n")

    val foldersProjs = Seq(CSFolder("src", "src", srcId), CSFolder("tests", "tests", testId)).map {
      f =>
        s"""Project("$folderProjectType") = "${f.name}", "${f.path.replace('/', '\\')}", "${f.folderId}"
           |EndProject""".stripMargin
    }.mkString("\n")

    val sln =
      s"""
         |Microsoft Visual Studio Solution File, Format Version 12.00
         |# Visual Studio 15
         |VisualStudioVersion = 15.0.26124.0
         |MinimumVisualStudioVersion = 15.0.26124.0
         |$foldersProjs
         |$projectDefs
         |Global
         |	GlobalSection(SolutionConfigurationPlatforms) = preSolution
         |		Debug|Any CPU = Debug|Any CPU
         |		Debug|x64 = Debug|x64
         |		Debug|x86 = Debug|x86
         |		Release|Any CPU = Release|Any CPU
         |		Release|x64 = Release|x64
         |		Release|x86 = Release|x86
         |	EndGlobalSection
         |	GlobalSection(SolutionProperties) = preSolution
         |		HideSolutionNode = FALSE
         |	EndGlobalSection
         |	GlobalSection(ProjectConfigurationPlatforms) = postSolution
         |$projectConfigs
         | 	EndGlobalSection
         |	GlobalSection(NestedProjects) = preSolution
         |$folders
         | 	EndGlobalSection
         |EndGlobal
     """.stripMargin

    val props = <Project>
    <PropertyGroup>
      <Deterministic>true</Deterministic>
      <BaseOutputPath>$(SolutionDir)/artifacts/target/$(MSBuildProjectName)</BaseOutputPath>
      <BaseIntermediateOutputPath>$(SolutionDir)/artifacts/tmp/$(MSBuildProjectName)</BaseIntermediateOutputPath>
    </PropertyGroup>
    </Project>

    val readme =
       s"""- Build:
          |
          |    msbuild /t:Restore /t:Rebuild
          |
          |- Test:
          |
          |    find ./artifacts/target -name "*.Test.dll" -print | xargs nunit-console
          |
          |- Pack nuspecs (in case generated aren't nice):
          |
          |    mkdir packages && cd packages && find ../nuspec -name '*.nuspec' -exec nuget pack {} \\;
       """.stripMargin.trim

    Seq(
      ExtendedModule.RuntimeModule(Module(ModuleId(Seq.empty, s"${naming.pkgId}.sln"), sln)),
      ExtendedModule.RuntimeModule(Module(ModuleId(Seq.empty, s"Directory.Build.props"), format(props))),
      ExtendedModule.RuntimeModule(Module(ModuleId(Seq.empty, s"README.md"), readme)),
    )

  }

  private def uid(): String = {
    s"{${UUID.randomUUID().toString}}".toUpperCase
  }

  private def csproj(pid: String, projDeps: Seq[String], deps: Seq[ManifestDependency]): Seq[ExtendedModule] = {
    val csproj = <Project Sdk="Microsoft.NET.Sdk">
      <PropertyGroup>
        <TargetFramework>{options.manifest.nuget.targetFramework}</TargetFramework>
        <GeneratePackageOnBuild>true</GeneratePackageOnBuild>
        <PackageVersion>{mfVersion}</PackageVersion>

        <IsPackable>true</IsPackable>
        <IncludeBuildOutput>false</IncludeBuildOutput>
        <ContentTargetFolders>contentFiles</ContentTargetFolders>
        <!--GenerateAssemblyInfo>false</GenerateAssemblyInfo-->
        <GenerateTargetFrameworkAttribute>false</GenerateTargetFrameworkAttribute>
        <GeneratePackageOnBuild>true</GeneratePackageOnBuild>
      </PropertyGroup>
        <ItemGroup>
          { deps.map{
          d =>
              <PackageReference Include={d.module} Version={d.version} />
          } }
        </ItemGroup>

        <ItemGroup>
          { projDeps.map(d => <ProjectReference Include={ s"$$(SolutionDir)/$d" } />) }
        </ItemGroup>

        <ItemGroup>
          <Reference Include="System.Web" />
        </ItemGroup>

        <ItemGroup>
          <Compile Update="@(Compile)">
            <Pack>true</Pack>
            <PackagePath>$(ContentTargetFolders)/cs/any/src/%(RecursiveDir)%(Filename)%(Extension)</PackagePath>
          </Compile>
        </ItemGroup>
    </Project>

    Seq(ExtendedModule.RuntimeModule(Module(ModuleId(Seq.empty, s"$pid.csproj"), format(csproj))))
  }

  private def generateNuspec(manifest: CSharpBuildManifest, id: String, filesFolder: List[String], deps: Seq[ManifestDependency]): String = {

    // TODO: use safe xml builder
    val mfCommon = manifest.common

     val out=  <package >
        <metadata>
          <id>{id}</id>
          <version>{renderVersion(mfCommon.version)}</version>
          <authors>{mfCommon.publisher.name}</authors>
          <owners>{mfCommon.publisher.id}</owners>
          <licenseUrl>{mfCommon.licenses.head.url.url}</licenseUrl>
          <projectUrl>{mfCommon.website.url}</projectUrl>
          <releaseNotes>{mfCommon.releaseNotes}</releaseNotes>
          <description>{mfCommon.description}</description>
          <copyright>{mfCommon.copyright}</copyright>
          <tags>{mfCommon.tags.mkString(" ")}</tags>
          <iconUrl>{manifest.nuget.iconUrl}</iconUrl>
          <requireLicenseAcceptance>{manifest.nuget.requireLicenseAcceptance}</requireLicenseAcceptance>
          <dependencies>
            {deps.map(d => <dependency id={d.module} version={d.version} />)}
          </dependencies>
          <contentFiles>
            <files include="**/*.cs" buildAction="Compile" copyToOutput="false" />
          </contentFiles>
        </metadata>
        <files>
          { filesFolder.map(ff => <file src={ff} target="contentFiles/cs/any/src" />) }
        </files>
      </package>
    format(out)
  }

  private def format(out: Elem): String = {
    s"""<?xml version="1.0"?>
      |${p.format(out)}""".stripMargin

  }
}

