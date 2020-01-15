package izumi.idealingua.translator.toscala.layout

import izumi.fundamentals.platform.build.ProjectAttributeMacro
import izumi.idealingua.model.common.DomainId
import izumi.idealingua.model.output.{Module, ModuleId}
import izumi.idealingua.model.publishing.manifests.ScalaProjectLayout
import izumi.idealingua.translator.CompilerOptions.ScalaTranslatorOptions
import izumi.idealingua.translator._

case class RawExpr(e: String)


class ScalaLayouter(options: ScalaTranslatorOptions) extends TranslationLayouter {
  private val naming = new ScalaNamingConvention(options.manifest.sbt.projectNaming)
  private val idlcGroupId = ProjectAttributeMacro.extractSbtProjectGroupId().getOrElse("UNSET-GROUP-ID")

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

        val rtid = DomainId(idlcGroupId.split('.'), "irt")

        val runtimeModules = asSbtModule(toRuntimeModules(options).map(_.module), rtid)
          .map(m => ExtendedModule.RuntimeModule(m))


        val projects = outputs
          .map {
            out =>
              naming.projectId(out.typespace.domain.id) -> out
          }
          .toMap

        val projIds = projects.keys.toList.sorted

        val projDefs = projIds
          .map {
            id =>
              val d = projects(id)
              val deps = d.typespace.domain.meta.directImports.map(i => s"`${naming.projectId(i.id)}`")

              val depends = if (deps.nonEmpty) {
                deps.mkString("\n    .dependsOn(\n        ", ",\n        ", "\n    )")
              } else {
                ""
              }

              s"""lazy val `$id` = (project in file("$id"))$depends"""
          }

        val bundleId = naming.bundleId
        val rootId = naming.pkgId

        val agg =
          s"""
             |lazy val `$rootId` = (project in file("."))
             |  .aggregate(
             |    ${(projIds ++ Seq(bundleId)).map(id => s"`$id`").mkString(",\n    ")}
             |  )
         """.stripMargin

        val allDeps = projIds.map(i => s"`$i`")
        val depends = if (allDeps.nonEmpty) {
          allDeps.mkString("\n    .dependsOn(\n        ", ",\n        ", "\n    )")
        } else {
          ""
        }

        val bundle =
          s"""
             |lazy val `$bundleId` = (project in file("$bundleId"))$depends
         """.stripMargin

        import SbtDslOp._

        val idlVersion = options.manifest.common.izumiVersion
        val deps = Seq("libraryDependencies" -> Append(
          Seq(
            RawExpr(s""" "$idlcGroupId" %% "idealingua-v1-runtime-rpc-scala" % "$idlVersion" """),
            RawExpr(s""" "$idlcGroupId" %% "idealingua-v1-model" % "$idlVersion" """),

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
          "version" -> Assign(renderVersion(options.manifest.common.version)),
          "homepage" -> Assign(Some(options.manifest.common.website)),
          "licenses" -> Append(options.manifest.common.licenses),
        )

        val renderer = new SbtRenderer()
        val keys = (metadata ++ resolvers ++ deps).map(renderer.renderOp)

        val content = keys ++ projDefs ++ Seq(bundle, agg)

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
        val pid = naming.projectId(did)
        m.copy(id = m.id.copy(path = Seq(pid, "src", "main", "scala") ++ m.id.path))
    }
  }


}

