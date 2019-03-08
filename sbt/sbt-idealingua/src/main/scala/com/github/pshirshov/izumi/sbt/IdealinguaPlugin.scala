package com.github.pshirshov.izumi.sbt

import java.nio.file.Path
import java.security.MessageDigest

import com.github.pshirshov.izumi.fundamentals.platform.files.IzFiles
import com.github.pshirshov.izumi.fundamentals.platform.time.IzTime
import com.github.pshirshov.izumi.idealingua.il.loader.{LocalModelLoaderContext, ModelResolver}
import com.github.pshirshov.izumi.idealingua.model.loader.UnresolvedDomains
import com.github.pshirshov.izumi.idealingua.model.publishing.manifests._
//import com.github.pshirshov.izumi.idealingua.translator.tocsharp.CSharpTranslator
//import com.github.pshirshov.izumi.idealingua.translator.tocsharp.extensions.CSharpTranslatorExtension
//import com.github.pshirshov.izumi.idealingua.translator.togolang.GoLangTranslator
//import com.github.pshirshov.izumi.idealingua.translator.togolang.extensions.GoLangTranslatorExtension
//import com.github.pshirshov.izumi.idealingua.translator.toscala.ScalaTranslator
//import com.github.pshirshov.izumi.idealingua.translator.toscala.extensions.ScalaTranslatorExtension
//import com.github.pshirshov.izumi.idealingua.translator.totypescript.TypeScriptTranslator
//import com.github.pshirshov.izumi.idealingua.translator.totypescript.extensions.TypeScriptTranslatorExtension
import com.github.pshirshov.izumi.idealingua.translator.{IDLLanguage, TypespaceCompilerBaseFacade, TypespaceCompilerFSFacade, UntypedCompilerOptions}
import sbt.Keys.{sourceGenerators, watchSources, _}
import sbt._
import sbt.internal.util.ConsoleLogger
import sbt.plugins._
import scalacache.MD5

object IdealinguaPlugin extends AutoPlugin {

  final case class Scope(project: ProjectRef, source: Path, buildTarget: Path, target: Path)

  sealed trait Mode

  object Mode {

    case object CompiledArtifact extends Mode

    case object SourceArtifact extends Mode

  }

  final case class Invokation(options: UntypedCompilerOptions, mode: Mode, subclassifier: Option[String] = None)

  object Keys {
    val compilationTargets = settingKey[Seq[Invokation]]("IDL targets")
    val unresolvedDomains = taskKey[UnresolvedDomains]("Loaded but not yet resolved domains")

//    val idlExtensionsScala = settingKey[Seq[ScalaTranslatorExtension]]("Default list of translator extensions for scala")
//    val idlExtensionsTypescript = settingKey[Seq[TypeScriptTranslatorExtension]]("Default list of translator extensions for typescript")
//    val idlExtensionsGolang = settingKey[Seq[GoLangTranslatorExtension]]("Default list of translator extensions for golang")
//    val idlExtensionsCSharp = settingKey[Seq[CSharpTranslatorExtension]]("Default list of translator extensions for csharp")

    val idlManifestScala = settingKey[ScalaBuildManifest]("scala manifest")
    val idlManifestTypescript = settingKey[TypeScriptBuildManifest]("typescript manifest")
    val idlManifestGolang = settingKey[GoLangBuildManifest]("golang manifest")
    val idlManifestCSharp = settingKey[CSharpBuildManifest]("csharp manifest")
  }

  import Keys._

  private val logger: ConsoleLogger = ConsoleLogger()

  override def requires = JvmPlugin

  implicit class ArtifactExt(a: Artifact) {
    def format: String = s"${a.name}${a.classifier.map(s => s"_$s").getOrElse("")}.${a.extension}"
  }

  implicit class FileExt(a: File) {
    def format: String = {
      val tpe = a match {
        case f if f.isDirectory =>
          "d"
        case f if f.isFile =>
          s"f:${f.length()}b"
        case f if !f.exists() =>
          ""
        case _ =>
          "?"
      }

      val existence = if (a.exists()) {
        "+"
      } else {
        "-"
      }

      s"[$existence$tpe]@${a.getCanonicalPath}"
    }
  }

  implicit class PathExt(a: Path) {
    def format: String = {
      a.toFile.format
    }
  }

  override lazy val projectSettings = Seq(
//    idlExtensionsScala := ScalaTranslator.defaultExtensions,
//    idlExtensionsTypescript := TypeScriptTranslator.defaultExtensions,
//    idlExtensionsGolang := GoLangTranslator.defaultExtensions,
//    idlExtensionsCSharp := CSharpTranslator.defaultExtensions,
    idlManifestScala := ScalaBuildManifest.example.copy(layout = ScalaProjectLayout.PLAIN),
    idlManifestTypescript := TypeScriptBuildManifest.example,
    idlManifestGolang := GoLangBuildManifest.example,
    idlManifestCSharp := CSharpBuildManifest.example,

    compilationTargets := Seq(
      Invokation(UntypedCompilerOptions(IDLLanguage.Scala, Seq.empty /*idlExtensionsScala.value*/, idlManifestScala.value), Mode.CompiledArtifact)
      , Invokation(UntypedCompilerOptions(IDLLanguage.Scala, Seq.empty /*idlExtensionsScala.value*/, idlManifestScala.value), Mode.SourceArtifact)

      , Invokation(UntypedCompilerOptions(IDLLanguage.Typescript, Seq.empty /*idlExtensionsTypescript.value*/, idlManifestTypescript.value), Mode.SourceArtifact)

      , Invokation(UntypedCompilerOptions(IDLLanguage.Go, Seq.empty /*idlExtensionsGolang.value*/, idlManifestGolang.value), Mode.SourceArtifact)

      , Invokation(UntypedCompilerOptions(IDLLanguage.CSharp, Seq.empty /*idlExtensionsCSharp.value*/, idlManifestCSharp.value), Mode.SourceArtifact)
    ),

    watchSources += Watched.WatchSource(baseDirectory.value / "src/main/izumi"),

    artifacts ++= {
      val ctargets = compilationTargets.value
      val pname = name.value
      artifactTargets(ctargets, pname).map(_._1)
    },

    packagedArtifacts := {
      val ctargets = compilationTargets.value
      val pname = name.value
      val src = sourceDirectory.value.toPath
      val versionValue = version.value
      val scalaVersionValue = scalaVersion.value
      val izumiSrcDir = src.resolve("main/izumi")

      val artifacts = artifactTargets(ctargets, pname)

      val artifactFiles = artifacts.flatMap {
        case (a, t) =>
          val targetDir = target.value / "idealingua" / s"${a.name}-${a.classifier.get}-$versionValue-$scalaVersionValue"
          val scope = Scope(thisProjectRef.value, izumiSrcDir, target.value.toPath, targetDir.toPath)
          val zipFile = targetDir / s"${a.name}-${a.classifier.get}-$versionValue.zip.source"
          val loaded = unresolvedDomains.value
          val result = generateCode(scope, t, loaded)

          result match {
            case Some(r) =>
              logger.info(s"${a.format}: Have new compilation result for artifact, copying ${r.zippedOutput.format} into ${zipFile.format}")
              IO.copyDirectory(r.zippedOutput.toFile, zipFile)
              logger.info(s"${a.format}: populated target ${zipFile.format}")
              Seq(a -> zipFile)

            case None =>
              if (zipFile.exists()) {
                logger.info(s"${a.format}: Compiler didn't return a result for artifact, reusing existing target ${zipFile.format}")
                Seq(a -> zipFile)
              } else {
                logger.info(s"${a.format}: Compiler didn't return a result for artifact, target is missing, What the fuck? Okay, let's return nothing :/ Missing target: ${zipFile.format}")
                Seq.empty
              }
          }

      }.toMap

      packagedArtifacts.value ++ artifactFiles
    },


    unresolvedDomains := {
      val projectId = thisProjectRef.value.project
      val src = sourceDirectory.value.toPath
      val izumiSrcDir = src.resolve("main/izumi")
      val overlaysSrcDir = src.resolve("main/izumi.overlay")

      logger.debug(s"""$projectId: Loading models from $izumiSrcDir...""")
      val depClasspath = (dependencyClasspath in Compile).value
      val cp = depClasspath.map(_.data)
      val loaded = new LocalModelLoaderContext(Seq(izumiSrcDir, overlaysSrcDir), cp).loader.load()
      logger.debug(s"""$projectId: Preloaded ${loaded.domains.results.size} domains from $izumiSrcDir...""")
      loaded
    },

    sourceGenerators in Compile += Def.task {
      val src = sourceDirectory.value.toPath
      val srcManaged = (sourceManaged in Compile).value.toPath
      //val resManaged = (resourceManaged in Compile).value.toPath

      val izumiSrcDir = src.resolve("main/izumi")


      val scope = Scope(thisProjectRef.value, izumiSrcDir, target.value.toPath, srcManaged)

      val (scalacInputTargets, nonScalacInputTargets) = compilationTargets
        .value
        .filter(_.mode == Mode.CompiledArtifact)
        .partition(i => i.options.language == IDLLanguage.Scala)

      if (nonScalacInputTargets.nonEmpty) {
        import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
        logger.warn(s"${name.value}: We don't know how to compile native artifacts for ${nonScalacInputTargets.niceList()}")
      }

      val loaded = unresolvedDomains.value

      val scala_result = scalacInputTargets
        .map {
          invokation =>
            (invokation, scope) -> generateCode(scope, invokation, loaded)
        }

      val files = scala_result.flatMap {
        case (_, Some(result)) =>
          result.compilationProducts.paths
        case ((_, s), _) if s.target.toFile.exists() =>
          val existing = IzFiles.walk(scope.target.toFile).filterNot(_.toFile.isDirectory)
          logger.info(s"${name.value}: Compiler didn't return a result, target ${s.target.format} exists. Reusing ${existing.size} files there...")
          existing
        case ((_, s), _) if !s.target.toFile.exists() =>
          logger.info(s"${name.value}: Compiler didn't return a result, target ${s.target.format} does not exist. What the fuck? Okay, let's return nothing :/")
          Seq.empty
      }

      files.map(_.toFile)
    }.taskValue,

    resourceGenerators in Compile += Def.task {
      val idlbase = sourceDirectory.value / "main" / "izumi"
      logger.debug(s"""${name.value}: Generating resources in ${idlbase.format} ...""")
      val allModels = (idlbase ** "*.domain").get ++ (idlbase ** "*.model").get
      val mapped = allModels.map {
        f =>
          val relative = idlbase.toPath.relativize(f.toPath)
          val targetPath = ((resourceManaged in Compile).value / "idealingua").toPath.resolve(relative).toFile
          f -> targetPath
      }
      IO.copy(mapped, CopyOptions().withOverwrite(true))
      mapped.map(_._2)
    }.taskValue,

  )

  private def artifactTargets(ctargets: Seq[Invokation], pname: String): Seq[(Artifact, Invokation)] = {
    ctargets
      .filter(i => i.mode == Mode.SourceArtifact)
      .map {
        target =>
          val classifier = target.subclassifier match {
            case Some(sc) =>
              s"${target.options.language.toString}_$sc"
            case None =>
              target.options.language.toString
          }

          Artifact(pname, "src", "zip", classifier) -> target
      }
  }

  private def generateCode(scope: Scope, invokation: Invokation, loaded: UnresolvedDomains): Option[TypespaceCompilerFSFacade.Result] = {
    val target = scope.target
    val projectId = scope.project.project

    val digest = MD5.messageDigest.clone().asInstanceOf[MessageDigest]
    digest.reset()
    val hash = digest.digest(scope.toString.getBytes).map("%02X".format(_)).mkString

    val tsCache = scope.buildTarget.resolve(s"izumi-$hash.timestamp")

    val srcLastModified = IzFiles.getLastModified(scope.source.toFile)
    val targetLastModified = IzFiles.getLastModified(tsCache.toFile)

    val isNew = srcLastModified.flatMap(src => targetLastModified.map(tgt => (src, tgt)))

    if (isNew.exists({ case (src, tgt) => src.isAfter(tgt) }) || isNew.isEmpty) {
      // TODO: maybe it's unsafe to destroy the whole directory?..
//      val rules = TypespaceCompilerBaseFacade.descriptor(invokation.options.language).rules
      val resolved = new ModelResolver().resolve(loaded, runt2 = false)

      val toCompile = resolved
        .ifWarnings(message => logger.warn(message))
        .ifFailed {
          message =>
            logger.error(s"Compiler failed:\n$message")
            throw new FeedbackProvidedException() {}
        }
        .successful

      if (toCompile.nonEmpty) {
        logger.info(s"""$projectId: Going to compile the following models: ${toCompile.map(_.typespace.domainId).mkString(",")} into ${invokation.options.language}""")
      } else {
        logger.info(s"""$projectId: Nothing to compile at ${scope.source}""")
      }

      val result = new TypespaceCompilerFSFacade(toCompile)
        .compile(target, invokation.options)
      logger.debug(s"$projectId: produced ${result.compilationProducts.paths.size} source files...")

      IO.write(tsCache.toFile, IzTime.isoNow)
      Some(result)
    } else {
      logger.info(s"""$projectId: Output timestamp is okay, not going to recompile ${scope.source.format} : ts=$tsCache""")
      None
    }
  }

  object autoImport {
    lazy val SbtIdealingua: IdealinguaPlugin.type = IdealinguaPlugin
    lazy val IdealinguaPluginKeys: Keys.type = IdealinguaPlugin.Keys
  }

}

