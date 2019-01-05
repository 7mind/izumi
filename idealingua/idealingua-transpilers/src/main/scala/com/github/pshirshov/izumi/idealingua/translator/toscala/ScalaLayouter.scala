package com.github.pshirshov.izumi.idealingua.translator.toscala

import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.output.{Module, ModuleId}
import com.github.pshirshov.izumi.idealingua.model.publishing.manifests.ScalaProjectLayout
import com.github.pshirshov.izumi.idealingua.translator.CompilerOptions.ScalaTranslatorOptions
import com.github.pshirshov.izumi.idealingua.translator.{ExtendedModule, Layouted, Translated, TranslationLayouter}

class ScalaLayouter(options: ScalaTranslatorOptions) extends TranslationLayouter {
  override def layout(outputs: Seq[Translated]): Layouted = {
    val modules = options.manifest.layout match {
      case ScalaProjectLayout.PLAIN =>
        withRuntime(options, outputs)

      case ScalaProjectLayout.SBT =>
        val projectModules = outputs.flatMap {
          out =>
            val did = out.typespace.domain.id

            asSbtModule(out.modules, did)
              .map(m => ExtendedModule.DomainModule(did, m))
        }

        val rtid = DomainId("com.github.pshirshov.izumi.idealingua.runtime".split('.'), "irt")

        val runtimeModules = asSbtModule(toRuntimeModules(options).map(_.module), rtid)
          .map(m => ExtendedModule.RuntimeModule(m))


        val projects = outputs
          .map {
            out =>
              projectId(out.typespace.domain.id) -> out
          }
          .toMap

        val projIds = projects.keys.toList.sorted

        val projDefs = projIds
          .map {
            id =>
              val d = projects(id)
              val deps = d.typespace.domain.meta.directImports.map(i => s"`${projectId(i.id)}`")

              val depends = if (deps.nonEmpty) {
                deps.mkString("\n    .dependsOn(\n        ", ",\n        ", "\n    )")
              } else {
                ""
              }

              s"""lazy val `$id` = (project in file("$id"))$depends"""
          }

        val idlVersion = "0.7.0-SNAPSHOT"

        val agg =
          s"""
             |lazy val root = (project in file("."))
             |  .aggregate(
             |    ${projIds.map(id => s"`$id`").mkString(",\n    ")}
             |  )
         """.stripMargin

        val deps =
          s"""libraryDependencies in ThisBuild ++= Seq(
             |  "com.github.pshirshov.izumi.r2" %% "idealingua-runtime-rpc-scala" % "$idlVersion",
             |  "com.github.pshirshov.izumi.r2" %% "idealingua-model" % "$idlVersion"
             |)
           """.stripMargin

        val resolvers = if (idlVersion.endsWith("SNAPSHOT")) {
          "resolvers in ThisBuild += Opts.resolver.sonatypeSnapshots"
        } else {
          "resolvers in ThisBuild += Opts.resolver.sonatypeReleases"
        }

        val content = Seq(resolvers, deps, agg) ++ projDefs

        val sbtModules = Seq(
          ExtendedModule.RuntimeModule(Module(ModuleId(Seq.empty, "build.sbt"), content.mkString("\n\n")))
        )

        projectModules ++ runtimeModules ++ sbtModules
    }
    Layouted(modules)
  }

  private def asSbtModule(out: Seq[Module], did: DomainId) = {
    out.map {
      m =>
        val pid = projectId(did)
        m.copy(id = m.id.copy(path = Seq(pid, "src", "main", "scala") ++ m.id.path))
    }
  }

  private def projectId(did: DomainId): String = {
    val shortened = if (options.manifest.dropPackageHead < 0) {
      did.id
    } else {
      val dropped = did.toPackage.drop(options.manifest.dropPackageHead)
      if (dropped.isEmpty) {
        did.id
      } else {
        dropped.mkString("-")
      }
    }
    shortened.toLowerCase
  }
}
