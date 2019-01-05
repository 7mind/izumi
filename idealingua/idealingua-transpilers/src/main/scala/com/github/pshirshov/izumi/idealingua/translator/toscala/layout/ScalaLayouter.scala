package com.github.pshirshov.izumi.idealingua.translator.toscala.layout

import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.output.{Module, ModuleId}
import com.github.pshirshov.izumi.idealingua.model.publishing.manifests.ScalaProjectLayout
import com.github.pshirshov.izumi.idealingua.translator.CompilerOptions.ScalaTranslatorOptions
import com.github.pshirshov.izumi.idealingua.translator.{ExtendedModule, Layouted, Translated, TranslationLayouter}

case class RawExpr(e: String)


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

        val agg =
          s"""
             |lazy val root = (project in file("."))
             |  .aggregate(
             |    ${projIds.map(id => s"`$id`").mkString(",\n    ")}
             |  )
         """.stripMargin

        import SbtDslOp._

        val idlVersion = options.manifest.common.izumiVersion
        val deps = Seq("libraryDependencies" -> Append(
          Seq(
            RawExpr(s""" "com.github.pshirshov.izumi.r2" %% "idealingua-runtime-rpc-scala" % "$idlVersion" """),
            RawExpr(s""" "com.github.pshirshov.izumi.r2" %% "idealingua-model" % "$idlVersion" """),

          )
        ))

        val resolvers = if (idlVersion.endsWith("SNAPSHOT")) {
          Seq("resolvers" -> Append(RawExpr("Opts.resolver.sonatypeSnapshots")))
        } else {
          Seq("resolvers" -> Append(RawExpr("Opts.resolver.sonatypeReleases")))
        }

        val metadata = Seq(
          "name" -> Assign(options.manifest.common.name, Scope.Project),
          "organization" -> Assign(options.manifest.common.group),
          "version" -> Assign(options.manifest.common.version),
          "homepage" -> Assign(Some(options.manifest.common.website)),
          "licenses" -> Append(options.manifest.common.licenses),
        )

        val renderer = new SbtRenderer()
        val keys = (metadata ++ resolvers ++ deps).map(renderer.renderOp)

        val content = keys ++ projDefs ++ Seq(agg)

        val sbtModules = Seq(
          ExtendedModule.RuntimeModule(Module(ModuleId(Seq.empty, "build.sbt"), content.map(_.trim).mkString("\n\n")))
        )

        projectModules ++ runtimeModules ++ sbtModules
    }
    Layouted(modules)
  }


  private def asSbtModule(out: Seq[Module], did: DomainId): Seq[Module] = {
    out.map {
      m =>
        val pid = projectId(did)
        m.copy(id = m.id.copy(path = Seq(pid, "src", "main", "scala") ++ m.id.path))
    }
  }

  private def projectId(did: DomainId): String = {
    val pkg = did.toPackage ++ options.manifest.projectIdPostfix.toSeq
    val parts = options.manifest.dropFQNSegments.getOrElse(0) match {
      case v if v < 0 =>
        pkg.takeRight(-v)
      case 0 =>
        Seq(pkg.last)
      case v =>
        pkg.drop(v) match {
          case Nil =>
            Seq(pkg.last)
          case shortened =>
            shortened
        }
    }
    parts.mkString("-").toLowerCase()
  }
}

