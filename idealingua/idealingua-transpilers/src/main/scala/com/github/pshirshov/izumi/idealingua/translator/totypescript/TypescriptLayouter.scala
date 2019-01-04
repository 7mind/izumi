package com.github.pshirshov.izumi.idealingua.translator.totypescript

import com.github.pshirshov.izumi.idealingua.model.common.TypeId.AliasId
import com.github.pshirshov.izumi.idealingua.model.output.{Module, ModuleId}
import com.github.pshirshov.izumi.idealingua.model.problems.IDLException
import com.github.pshirshov.izumi.idealingua.model.publishing.ManifestDependency
import com.github.pshirshov.izumi.idealingua.model.publishing.manifests.{TypeScriptBuildManifest, TypeScriptModuleSchema}
import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace
import com.github.pshirshov.izumi.idealingua.translator.CompilerOptions.TypescriptTranslatorOptions
import com.github.pshirshov.izumi.idealingua.translator.{ExtendedModule, Layouted, Translated, TranslationLayouter}
import io.circe.Json
import io.circe.literal._
import io.circe.syntax._

class TypescriptLayouter(options: TypescriptTranslatorOptions) extends TranslationLayouter {
  val tsManifest: Option[TypeScriptBuildManifest] = options.manifest


  override def layout(outputs: Seq[Translated]): Layouted = {
    val modules = outputs.flatMap(applyLayout)
    val rt = toRuntimeModules(options)

    val withLayout = options.manifest match {
      case Some(mf) if mf.moduleSchema == TypeScriptModuleSchema.PER_DOMAIN =>
        val inSubdir = modules
        val inRtSubdir = addPrefix(rt ++ Seq(ExtendedModule.RuntimeModule(buildIRTPackageModule(mf))), mf.scope)
        addPrefix(inSubdir ++ inRtSubdir, "packages") ++ buildRootModules(mf)
      case Some(mf) =>
        modules ++ rt ++ buildRootModules(mf)
      case None =>
        throw new IDLException(s"Manifest is mandatory for typescript!")
    }

    Layouted(withLayout)
  }

  private def addPrefix(rt: Seq[ExtendedModule], prefix: String): Seq[ExtendedModule] = {
    rt.map {
      case ExtendedModule.DomainModule(domain, module) =>
        ExtendedModule.DomainModule(domain, module.copy(id = module.id.copy(path = prefix +: module.id.path)))
      case ExtendedModule.RuntimeModule(module) =>
        ExtendedModule.RuntimeModule(module.copy(id = module.id.copy(path = prefix +: module.id.path)))
    }
  }

  private def buildRootModules(mf: TypeScriptBuildManifest): Seq[ExtendedModule.RuntimeModule] = {
    val rootDir = if (mf.moduleSchema == TypeScriptModuleSchema.PER_DOMAIN) {
      "packages"
    } else {
      "."
    }
    val tsconfig =
      json"""
            {
                "compilerOptions": {
                  "module": "commonjs",
                  "target": "es5",
                  "lib": ["es6", "dom"],
                  "sourceMap": true,
                  "allowJs": false,
                  "moduleResolution": "node",
                  "rootDirs": [$rootDir],
                  "outDir": "dist",
                  "declaration": true,
                  "baseUrl": ".",
                  "paths": {
                    "*": [
                      ${s"$rootDir/*"},
                      "node_modules/*"
                    ]
                  },
                  "forceConsistentCasingInFileNames": true,
                  "noImplicitReturns": true,
                  "noImplicitThis": true,
                  "noImplicitAny": true,
                  "strictNullChecks": false,
                  "suppressImplicitAnyIndexErrors": true,
                  "experimentalDecorators": true,
                  "removeComments": true,
                 "preserveConstEnums": true
                },
                "compileOnSave": false
              }
          """.toString()

    val packageJson = generatePackage(mf, None, "root", List.empty)
    val rootJson =
      json"""{
            "private": true,
            "workspaces": {
              "packages": [${s"packages/${mf.scope}/*"}]
            }
          }"""
    val fullRootJson = packageJson.deepMerge(rootJson)

    Seq(
      ExtendedModule.RuntimeModule(Module(ModuleId(Seq.empty, "package.json"), fullRootJson.toString())),
      ExtendedModule.RuntimeModule(Module(ModuleId(Seq.empty, "tsconfig.json"), tsconfig)),
    )
  }

  private def applyLayout(translated: Translated): Seq[ExtendedModule.DomainModule] = {
    val ts = translated.typespace
    val modules = translated.modules ++ (
      if (tsManifest.exists(_.moduleSchema == TypeScriptModuleSchema.PER_DOMAIN))
        List(
          buildIndexModule(ts),
          buildPackageModule(ts, tsManifest.get),
        )
      else
        List(buildIndexModule(ts))
      )


    val mm = if (tsManifest.exists(_.moduleSchema == TypeScriptModuleSchema.PER_DOMAIN)) {
      modules.map {
        m =>
          m.copy(id = toScopedId(m.id))
      }
    } else {
      modules
    }
    mm.map(m => ExtendedModule.DomainModule(translated.typespace.domain.id, m))
  }

  private def toDirName(parts: Seq[String], mf: TypeScriptBuildManifest): String = {
    val dropped = mf.dropNameSpaceSegments.fold(parts)(toDrop => parts.drop(toDrop))
    dropped.mkString("-")
  }

  private def toScopedId(parts: Seq[String], mf: TypeScriptBuildManifest): String = {
    s"${mf.scope}/${toDirName(parts, mf)}"
  }

  private def toScopedId(id: ModuleId): ModuleId = {
    val path = Seq(
      tsManifest.get.scope,
      makeName(id)
    )

    ModuleId(path, id.name)
  }

  private def makeName(m: ModuleId): String = {
    (
      if (tsManifest.get.dropNameSpaceSegments.isDefined)
        m.path.drop(tsManifest.get.dropNameSpaceSegments.get)
      else
        m.path
      ).mkString("-")
  }

  def buildPackageModule(ts: Typespace, mf: TypeScriptBuildManifest): Module = {
    val peerDeps = ts.domain.meta.directImports
      .map {
        i =>
          ManifestDependency(toScopedId(i.id.toPackage, mf), mf.version)
      } :+ ManifestDependency(toScopedId(List("irt"), mf), mf.version)


    val name = toScopedId(ts.domain.id.toPackage, mf)

    val content = generatePackage(mf, Some("index"), name, peerDeps.toList)
    Module(ModuleId(ts.domain.id.toPackage, "package.json"), content.toString())
  }

  def buildIRTPackageModule(manifest: TypeScriptBuildManifest): Module = {
    val content = generatePackage(manifest.copy(dropNameSpaceSegments = None), Some("index"), toScopedId(List("irt"), manifest))
    Module(ModuleId(Seq("irt"), "package.json"), content.toString())
  }

  def buildIndexModule(ts: Typespace): Module = {
    val content =
      s"""// Auto-generated, any modifications may be overwritten in the future.
         |// Exporting module for domain ${ts.domain.id.toPackage.mkString(".")}
         |${ts.domain.types.filterNot(_.id.isInstanceOf[AliasId]).map(t => s"export * from './${t.id.name}';").mkString("\n")}
         |${ts.domain.services.map(s => s"export * from './${s.id.name}';").mkString("\n")}
         """.stripMargin

    Module(ModuleId(ts.domain.id.toPackage, "index.ts"), content)
  }

  def generatePackage(manifest: TypeScriptBuildManifest, main: Option[String], name: String, peerDependencies: List[ManifestDependency] = List.empty): Json = {
    val author = s"${manifest.publisher.name} (${manifest.publisher.id})"
    val deps = manifest.dependencies.map(d => d.module -> d.version).toMap.asJson
    val peerDeps = peerDependencies.map(d => d.module -> d.version).toMap.asJson

    val base =
      json"""{
         "name": $name,
         "version": ${manifest.version},
         "description": ${manifest.description},
         "author": $author,
         "license": ${manifest.license},
         "dependencies": $deps,
         "peerDependencies": $peerDeps
       }
     """


    main match {
      case Some(value) =>
        base.deepMerge(
          json"""{"main": ${s"$value.js"}, "typings": ${s"$value.d.ts"}}""")
      case None =>
        base
    }
  }
}
