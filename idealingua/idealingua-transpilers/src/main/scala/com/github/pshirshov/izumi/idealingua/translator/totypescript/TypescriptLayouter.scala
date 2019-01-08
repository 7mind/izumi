package com.github.pshirshov.izumi.idealingua.translator.totypescript

import com.github.pshirshov.izumi.idealingua.model.common.TypeId.AliasId
import com.github.pshirshov.izumi.idealingua.model.output.{Module, ModuleId}
import com.github.pshirshov.izumi.idealingua.model.publishing.BuildManifest.ManifestDependency
import com.github.pshirshov.izumi.idealingua.model.publishing.ProjectVersion
import com.github.pshirshov.izumi.idealingua.model.publishing.manifests.{TypeScriptBuildManifest, TypeScriptProjectLayout}
import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace
import com.github.pshirshov.izumi.idealingua.translator.CompilerOptions.TypescriptTranslatorOptions
import com.github.pshirshov.izumi.idealingua.translator.{ExtendedModule, Layouted, Translated, TranslationLayouter}
import io.circe.Json
import io.circe.literal._
import io.circe.syntax._

class TypescriptLayouter(options: TypescriptTranslatorOptions) extends TranslationLayouter {

  override def layout(outputs: Seq[Translated]): Layouted = {
    val modules = outputs.flatMap(applyLayout)
    val rt = toRuntimeModules(options)

    val withLayout = if (options.manifest.layout == TypeScriptProjectLayout.YARN) {
      val inSubdir = modules
      val inRtSubdir = addPrefix(rt ++ Seq(ExtendedModule.RuntimeModule(buildIRTPackageModule())), Seq(options.manifest.yarn.scope))
      addPrefix(inSubdir ++ inRtSubdir, Seq("packages")) ++ buildRootModules(options.manifest)
    } else {
      modules ++ rt ++ buildRootModules(options.manifest)
    }

    Layouted(withLayout)
  }

  private def applyLayout(translated: Translated): Seq[ExtendedModule.DomainModule] = {
    val ts = translated.typespace
    val modules = translated.modules ++ (
      if (options.manifest.layout == TypeScriptProjectLayout.YARN)
        List(
          buildIndexModule(ts),
          buildPackageModule(ts),
        )
      else
        List(buildIndexModule(ts))
      )


    val mm = if (options.manifest.layout == TypeScriptProjectLayout.YARN) {
      modules.map {
        m =>
          m.copy(id = toScopedId(m.id))
      }
    } else {
      modules
    }
    mm.map(m => ExtendedModule.DomainModule(translated.typespace.domain.id, m))
  }



  private def buildRootModules(mf: TypeScriptBuildManifest): Seq[ExtendedModule.RuntimeModule] = {
    val rootDir = if (mf.layout == TypeScriptProjectLayout.YARN) {
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
              "packages": [${s"packages/${mf.yarn.scope}/*"}]
            },
            "scripts": {
              "build": "tsc"
            }
          }"""
    val fullRootJson = packageJson.deepMerge(rootJson)

    Seq(
      ExtendedModule.RuntimeModule(Module(ModuleId(Seq.empty, "package.json"), fullRootJson.toString())),
      ExtendedModule.RuntimeModule(Module(ModuleId(Seq.empty, "tsconfig.json"), tsconfig)),
    )
  }

  private def toDirName(parts: Seq[String]): String = {
    val dropped = options.manifest.yarn.dropFQNSegments.fold(parts)(toDrop => parts.drop(toDrop))
    dropped.mkString("-")
  }

  private def toScopedId(parts: Seq[String]): String = {
    s"${options.manifest.yarn.scope}/${toDirName(parts)}"
  }

  private def toScopedId(id: ModuleId): ModuleId = {
    val path = Seq(options.manifest.yarn.scope, makeName(id))

    ModuleId(path, id.name)
  }

  private def makeName(m: ModuleId): String = {
    (
      if (options.manifest.yarn.dropFQNSegments.isDefined)
        m.path.drop(options.manifest.yarn.dropFQNSegments.get)
      else
        m.path
      ).mkString("-")
  }

  private def buildPackageModule(ts: Typespace): Module = {
    val peerDeps = ts.domain.meta.directImports
      .map {
        i =>
          ManifestDependency(toScopedId(i.id.toPackage), renderVersion(options.manifest.common.version))
      } :+ ManifestDependency(irtDependency, renderVersion(options.manifest.common.version))


    val name = toScopedId(ts.domain.id.toPackage)

    val content = generatePackage(options.manifest, Some("index"), name, peerDeps.toList)
    Module(ModuleId(ts.domain.id.toPackage, "package.json"), content.toString())
  }
  private def irtDependency: String = s"${options.manifest.yarn.scope}/irt"

  private def buildIRTPackageModule(): Module = {
    val content = generatePackage(options.manifest.copy(yarn = options.manifest.yarn.copy(dropFQNSegments = None)), Some("index"), irtDependency)
    Module(ModuleId(Seq("irt"), "package.json"), content.toString())
  }

  private def buildIndexModule(ts: Typespace): Module = {
    val content =
      s"""// Auto-generated, any modifications may be overwritten in the future.
         |// Exporting module for domain ${ts.domain.id.toPackage.mkString(".")}
         |${ts.domain.types.filterNot(_.id.isInstanceOf[AliasId]).map(t => s"export * from './${t.id.name}';").mkString("\n")}
         |${ts.domain.services.map(s => s"export * from './${s.id.name}';").mkString("\n")}
         """.stripMargin

    Module(ModuleId(ts.domain.id.toPackage, "index.ts"), content)
  }

  private def generatePackage(manifest: TypeScriptBuildManifest, main: Option[String], name: String, peerDependencies: List[ManifestDependency] = List.empty): Json = {
    val author = s"${manifest.common.publisher.name} (${manifest.common.publisher.id})"
    val deps = manifest.yarn.dependencies.map(d => d.module -> d.version).toMap.asJson
    val peerDeps = peerDependencies.map(d => d.module -> d.version).toMap.asJson

    val base =
      json"""{
         "name": $name,
         "version": ${renderVersion(manifest.common.version)},
         "description": ${manifest.common.description},
         "author": $author,
         "license": ${manifest.common.licenses.head.name},
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

  private def renderVersion(version: ProjectVersion): String = {
    if (version.release) {
      s"${version.version}"
    } else {
      s"${version.version}-${version.snapshotQualifier}"
    }
  }
}
