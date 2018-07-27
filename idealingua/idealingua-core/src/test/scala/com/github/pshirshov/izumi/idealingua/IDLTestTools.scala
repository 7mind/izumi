package com.github.pshirshov.izumi.idealingua

import java.io.File
import java.lang.management.ManagementFactory
import java.nio.charset.StandardCharsets
import java.nio.file._

import com.github.pshirshov.izumi.fundamentals.platform.build.ExposedTestScope
import com.github.pshirshov.izumi.fundamentals.platform.files.IzFiles
import com.github.pshirshov.izumi.fundamentals.platform.jvm.IzJvm
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.fundamentals.platform.resources.IzResources
import com.github.pshirshov.izumi.idealingua.il.loader.LocalModelLoader
import com.github.pshirshov.izumi.idealingua.il.renderer.ILRenderer
import com.github.pshirshov.izumi.idealingua.model.publishing.manifests.{GoLangBuildManifest, TypeScriptBuildManifest, TypeScriptModuleSchema}
import com.github.pshirshov.izumi.idealingua.model.publishing.{BuildManifest, ManifestDependency, Publisher}
import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace
import com.github.pshirshov.izumi.idealingua.translator.TypespaceCompiler.{CompilerOptions, UntypedCompilerOptions}
import com.github.pshirshov.izumi.idealingua.translator.tocsharp.CSharpTranslator
import com.github.pshirshov.izumi.idealingua.translator.tocsharp.extensions.CSharpTranslatorExtension
import com.github.pshirshov.izumi.idealingua.translator.togolang.GoLangTranslator
import com.github.pshirshov.izumi.idealingua.translator.togolang.extensions.GoLangTranslatorExtension
import com.github.pshirshov.izumi.idealingua.translator.toscala.ScalaTranslator
import com.github.pshirshov.izumi.idealingua.translator.toscala.extensions.ScalaTranslatorExtension
import com.github.pshirshov.izumi.idealingua.translator.totypescript.TypeScriptTranslator
import com.github.pshirshov.izumi.idealingua.translator.totypescript.extensions.TypeScriptTranslatorExtension
import com.github.pshirshov.izumi.idealingua.translator.{IDLCompiler, IDLLanguage, TranslatorExtension}

import scala.sys.process._

@ExposedTestScope
final case class CompilerOutput(targetDir: Path, allFiles: Seq[Path]) {
  def absoluteTargetDir: Path = targetDir.toAbsolutePath

  def phase3: Path = absoluteTargetDir.getParent.resolve("phase3-compiler-output")

  def phase3Relative: Path = absoluteTargetDir.relativize(phase3)

  def relativeOutputs: Seq[String] = allFiles.map(p => absoluteTargetDir.relativize(p.toAbsolutePath).toString)
}


@ExposedTestScope
object IDLTestTools {
  def makeLoader(): LocalModelLoader = {
    val src = new File(getClass.getResource("/defs").toURI).toPath
    new LocalModelLoader(src, Seq.empty)
  }

  def loadDefs(): Seq[Typespace] = loadDefs(makeLoader())

  def loadDefs(loader: LocalModelLoader): Seq[Typespace] = {
    val loaded = loader.load()

    val loadableCount = loader.enumerate().count(_._1.toString.endsWith(LocalModelLoader.domainExt))
    assert(loaded.size == loadableCount, s"expected $loadableCount domains")

    loaded
  }

  def compilesScala(id: String, domains: Seq[Typespace], extensions: Seq[ScalaTranslatorExtension] = ScalaTranslator.defaultExtensions): Boolean = {
    val out = compiles(id, domains, CompilerOptions(IDLLanguage.Scala, extensions))
    val classLoader = Thread
      .currentThread
      .getContextClassLoader
      .getParent


    val classpath: String = IzJvm.safeClasspath(classLoader)

    val cmd = Seq(
      "scalac"
      , "-deprecation"
      , "-opt-warnings:_"
      , "-d", out.phase3Relative.toString
      , "-classpath", classpath
    ) ++ out.relativeOutputs

    val exitCode = run(out.absoluteTargetDir, cmd, Map.empty, "scalac")
    exitCode == 0
  }

  def compilesTypeScript(id: String, domains: Seq[Typespace], extensions: Seq[TypeScriptTranslatorExtension] = TypeScriptTranslator.defaultExtensions, scoped: Boolean): Boolean = {
    val manifest = new TypeScriptBuildManifest(
        name = "TestBuild",
        tags = "",
        description = "Test Description",
        notes = "",
        publisher = Publisher("Test Publisher Name", "test_publisher_id"),
        version = "0.0.0",
        license = "MIT",
        website = "http://project.website",
        copyright = "Copyright (C) Test Inc.",
        dependencies = List(ManifestDependency("moment", "^2.20.1")),
        scope = "@TestScope",
        moduleSchema = if (scoped) TypeScriptModuleSchema.PER_DOMAIN else TypeScriptModuleSchema.UNITED
      )

    val out = compiles(id, domains, CompilerOptions(IDLLanguage.Typescript, extensions, true, if(scoped) Some(manifest) else None))

//    if (scoped) {
//      val transformer =
//      s"""SCOPE=${manifest.scope}
//         |
//         |echo "Packages found:"
//         |find . -name package.json -print0 | xargs -0 -n1 dirname | tr -d . | tr / - | sed 's/-//' | sort --unique
//         |
//         |rm -rf $$SCOPE
//         |mkdir -p $$SCOPE
//         |echo
//         |echo "Transforming packages into modules structure:"
//         |for F in $$(find . -name package.json); do
//         |    SRC=$$(dirname $$F)
//         |    DST=$$(echo $$SRC| tr -d . | tr / - | sed 's/-//')
//         |    DST="$${SCOPE}/$${DST}"
//         |    echo "Coping from $$SRC into $$DST"
//         |    cp -r $$SRC $$DST
//         |    rm -rf $$SRC
//         |done
//       """.stripMargin
//
//      val transformPath = out.targetDir.resolve("transform.sh")
//      Files.write(transformPath, transformer.getBytes)
//      val transformCmd = Seq("sh", "transform.sh")
//      if (run(out.absoluteTargetDir, transformCmd, Map.empty, "sh") != 0) {
//        return false
//      }
//    }

    val outputTspackagePath = out.targetDir.resolve("package.json")
    Files.write(outputTspackagePath, TypeScriptBuildManifest.generatePackage(manifest, "index", "TestPackage", List.empty).getBytes)
    val npmCmd = Seq("npm", "install")
    if (run(out.absoluteTargetDir, npmCmd, Map.empty, "npm") != 0) {
      return false
    }

    val outputTsconfigPath = out.targetDir.resolve("tsconfig.json")
    val tsconfigBytes = IzResources.readAsString("tsconfig-compiler-test.json")
      .get
      .replace("../phase3-compiler-output", out.phase3.toString)
    Files.write(outputTsconfigPath, tsconfigBytes.getBytes)
    val tscCmd = Seq("tsc", "-p", outputTsconfigPath.toFile.getName)

    val exitCode = run(out.absoluteTargetDir, tscCmd, Map.empty, "tsc")
    exitCode == 0
  }

  def compilesCSharp(id: String, domains: Seq[Typespace], extensions: Seq[CSharpTranslatorExtension] = CSharpTranslator.defaultExtensions): Boolean = {
    val lang = IDLLanguage.CSharp
    val out = compiles(id, domains, CompilerOptions(lang, extensions))
    val refsDir = out.absoluteTargetDir.resolve("refs")

    IzFiles.recreateDirs(refsDir)

    val refsSrc = s"refs/${lang.toString.toLowerCase()}"
    val refDlls = IzResources.copyFromClasspath(refsSrc, refsDir).files
      .filter(f => f.toFile.isFile && f.toString.endsWith(".dll")).map(f => out.absoluteTargetDir.relativize(f.toAbsolutePath))
    IzResources.copyFromClasspath(refsSrc, out.phase3)


    val outname = "test-output.dll"
    val refs = s"/reference:${refDlls.mkString(",")}"
    val cmdBuild = Seq("csc", "-target:library", s"-out:${out.phase3Relative}/$outname", "-recurse:\\*.cs", refs)
    val exitCodeBuild = run(out.absoluteTargetDir, cmdBuild, Map.empty, "cs-build")

    val cmdTest = Seq("nunit-console", outname)
    val exitCodeTest = run(out.phase3, cmdTest, Map.empty, "cs-test")

    exitCodeBuild == 0 && exitCodeTest == 0
  }

  def compilesGolang(id: String, domains: Seq[Typespace], extensions: Seq[GoLangTranslatorExtension] = GoLangTranslator.defaultExtensions, scoped: Boolean): Boolean = {
    val manifest = new GoLangBuildManifest(
      name = "TestBuild",
      tags = "",
      description = "Test Description",
      notes = "",
      publisher = Publisher("Test Publisher Name", "test_publisher_id"),
      version = "0.0.0",
      license = "MIT",
      website = "http://project.website",
      copyright = "Copyright (C) Test Inc.",
      dependencies = List(ManifestDependency("moment", "^2.20.1")),
      repository = "github.com/TestCompany/TestRepo",
      useRepositoryFolders = true
    )

    val out = compiles(id, domains, CompilerOptions(IDLLanguage.Go, extensions, true, if(scoped) Some(manifest) else None))
    val outDir = out.absoluteTargetDir

    val tmp = outDir.getParent.resolve("phase2-compiler-tmp")
    tmp.toFile.mkdirs()
    Files.move(outDir, tmp.resolve("src"))
    Files.move(tmp, outDir)

    val cmdBuild = Seq("go", "install", "-pkgdir", out.phase3.toString, "./...")
    val cmdTest = Seq("go", "test", "./...")

    val env = Map("GOPATH" -> out.absoluteTargetDir.toString)
    val goSrc = out.absoluteTargetDir.resolve("src")
    val exitCodeBuild = run(goSrc, cmdBuild, env, "go-build")
    val exitCodeTest = run(goSrc, cmdTest, env, "go-test")

    exitCodeBuild == 0 && exitCodeTest == 0
  }

  private def compiles[E <: TranslatorExtension, M <: BuildManifest](id: String, domains: Seq[Typespace], options: CompilerOptions[E, M]): CompilerOutput = {
    val targetDir = Paths.get("target")
    val tmpdir = targetDir.resolve("idl-output")

    Quirks.discard(tmpdir.toFile.mkdirs())

    // TODO: clashes still may happen in case of parallel runs with the same ID
    val stablePrefix = s"$id-${options.language.toString}"
    val vmPrefix = s"$stablePrefix-u${ManagementFactory.getRuntimeMXBean.getStartTime}"
    val dirPrefix = s"$vmPrefix-ts${System.currentTimeMillis()}"

    dropOldRunsData(tmpdir, stablePrefix, vmPrefix)

    val runDir = tmpdir.resolve(dirPrefix)
    val domainsDir = runDir.resolve("phase0-rerender")
    val layoutDir = runDir.resolve("phase1-layout")
    val compilerDir = runDir.resolve("phase2-compiler-input")

    IzFiles.recreateDirs(runDir, domainsDir, layoutDir, compilerDir)
    IzFiles.refreshSymlink(targetDir.resolve(stablePrefix), runDir)

    //val options = TypespaceCompiler.UntypedCompilerOptions(language, extensions)

    val allFiles: Seq[Path] = new IDLCompiler(domains)
      .compile(compilerDir, UntypedCompilerOptions(options.language, options.extensions, options.withRuntime, options.manifest))
      .compilationProducts.flatMap {
      case (did, s) =>
//        val mapped = s.paths.map {
//          f =>
//            val domainDir = layoutDir.resolve(did.toPackage.mkString("."))
//            (f, domainDir.resolve(f.toFile.getName))
//        }
//
//        mapped.foreach {
//          case (src, tgt) =>
//            tgt.getParent.toFile.mkdirs()
////            println(s"Copying from ${src} to ${tgt}")
//            Files.copy(src, tgt)
//        }
//
//        assert(s.paths.toSet.size == s.paths.size)

        s.paths
    }.toSeq

    domains.foreach {
      d =>
        val rendered = new ILRenderer(d.domain).render()
        Files.write(domainsDir.resolve(s"${d.domain.id.id}.domain"), rendered.getBytes(StandardCharsets.UTF_8))
    }

    val out = CompilerOutput(compilerDir, allFiles)
    out.phase3.toFile.mkdirs()
    out
  }


  private def dropOldRunsData(tmpdir: Path, stablePrefix: String, vmPrefix: String): Unit = {
    tmpdir
      .toFile
      .listFiles()
      .toList
      .filter(f => f.isDirectory && f.getName.startsWith(stablePrefix) && !f.getName.startsWith(vmPrefix))
      .foreach {
        f =>
          Quirks.discard(IzFiles.removeDir(f.toPath))
      }
  }

  private def run(workDir: Path, cmd: Seq[String], env: Map[String, String], cname: String): Int = {
    val cmdlog = workDir.getParent.resolve(s"$cname.sh")
    val commands = Seq(s"cd ${workDir.toAbsolutePath}") ++ env.map(kv => s"export ${kv._1}=${kv._2}") ++ Seq(cmd.mkString(" "))
    val cmdSh = commands.mkString("\n")
    Files.write(cmdlog, cmdSh.getBytes)

    val log = workDir.getParent.resolve(s"$cname.log").toFile
    val logger = ProcessLogger(log)
    val exitCode = try {
      Process(cmd, Some(workDir.toFile), env.toSeq: _*)
        .run(logger)
        .exitValue()
    } finally {
      logger.close()
    }

    if (exitCode != 0) {
      System.err.println(s"Process failed for $cname: $exitCode")
      println(cmdSh)
      System.err.println(IzFiles.readString(log))
    }
    exitCode
  }
}
