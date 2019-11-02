package izumi.idealingua.translator.totypescript.layout

import izumi.idealingua.model.common.TypeId.AliasId
import izumi.idealingua.model.output.{Module, ModuleId}
import izumi.idealingua.model.publishing.BuildManifest.ManifestDependency
import izumi.idealingua.model.publishing.manifests.{TypeScriptBuildManifest, TypeScriptProjectLayout}
import izumi.idealingua.model.typespace.Typespace
import izumi.idealingua.translator.CompilerOptions.TypescriptTranslatorOptions
import izumi.idealingua.translator._
import io.circe.Json
import io.circe.literal._
import io.circe.syntax._


class TypescriptLayouter(options: TypescriptTranslatorOptions) extends TranslationLayouter {
  private val naming = new TypescriptNamingConvention(options.manifest)

  override def layout(outputs: Seq[Translated]): Layouted = {
    val modules = outputs.flatMap(applyLayout)
    val rt = toRuntimeModules(options)

    val withLayout = if (options.manifest.layout == TypeScriptProjectLayout.YARN) {
      val inSubdir = modules
      val inRtSubdir = addPrefix(rt ++ Seq(ExtendedModule.RuntimeModule(buildIRTPackageModule())), Seq(options.manifest.yarn.scope))
      val inBundleSubdir = buildBundlePackageModules(outputs)

      addPrefix(inSubdir ++ inRtSubdir ++ inBundleSubdir, Seq("packages")) ++
        buildRootModules(options.manifest)

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

    val packageJson = generatePackage(mf, None, "root")
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
      Module(ModuleId(Seq.empty, "package.json"), fullRootJson.toString()),
      Module(ModuleId(Seq.empty, "tsconfig.json"), tsconfig),
    ).map(ExtendedModule.RuntimeModule)
  }


  private def toScopedId(id: ModuleId): ModuleId = {
    val path = Seq(options.manifest.yarn.scope, naming.makeName(id))

    ModuleId(path, id.name)
  }


  private def buildPackageModule(ts: Typespace): Module = {
    val allDeps = ts.domain.meta.directImports
      .map {
        i =>
          ManifestDependency(naming.toScopedId(i.id.toPackage), mfVersion)
      } :+ ManifestDependency(naming.irtDependency, mfVersion)


    val name = naming.toScopedId(ts.domain.id.toPackage)

    val mf = options.manifest.copy(yarn = options.manifest.yarn.copy(dependencies = options.manifest.yarn.dependencies ++ allDeps))
    val content = generatePackage(mf, Some("index"), name)
    Module(ModuleId(ts.domain.id.toPackage, "package.json"), content.toString())
  }


  private def buildIRTPackageModule(): Module = {
    val content = generatePackage(options.manifest, Some("index"), naming.irtDependency)
    Module(ModuleId(Seq("irt"), "package.json"), content.toString())
  }

  private def buildBundlePackageModules(translated: Seq[Translated]): Seq[ExtendedModule.RuntimeModule] = {
    val allDeps = translated.map {
      ts =>
        ManifestDependency(naming.toScopedId(ts.typespace.domain.id.toPackage), mfVersion)
    }

    val mf = options.manifest.copy(yarn = options.manifest.yarn.copy(dependencies = options.manifest.yarn.dependencies ++ allDeps))
    val content = generatePackage(mf, None, naming.bundleId)
    Seq(
      ExtendedModule.RuntimeModule(Module(ModuleId(Seq(naming.bundleId), "package.json"), content.toString())),
      ExtendedModule.RuntimeModule(Module(ModuleId(Seq(naming.bundleId), "empty.ts"), "")),
    )
  }

  private def mfVersion: String = {
    renderVersion(options.manifest.common.version)
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

  private def generatePackage(manifest: TypeScriptBuildManifest, main: Option[String], name: String): Json = {
    val author = s"${manifest.common.publisher.name} (${manifest.common.publisher.id})"
    val deps = manifest.yarn.dependencies.map(d => d.module -> d.version).toMap.asJson
    val devDeps = manifest.yarn.devDependencies.map(d => d.module -> d.version).toMap.asJson

    val base =
      json"""{
         "name": $name,
         "version": ${renderVersion(manifest.common.version)},
         "description": ${manifest.common.description},
         "author": $author,
         "license": ${manifest.common.licenses.head.name},
         "dependencies": $deps,
         "devDependencies": $devDeps
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
