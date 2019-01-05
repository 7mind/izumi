package com.github.pshirshov.izumi.idealingua.translator.toscala

import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.output.{Module, ModuleId}
import com.github.pshirshov.izumi.idealingua.model.publishing.BuildManifest
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
          "homepage" -> Assign(Some(options.manifest.common.website)),
          "licenses" -> Append(options.manifest.common.licenses),
        )

        val keys = (metadata ++ resolvers ++ deps).map(renderOp)

        val content = keys ++ projDefs ++ Seq(agg)

        val sbtModules = Seq(
          ExtendedModule.RuntimeModule(Module(ModuleId(Seq.empty, "build.sbt"), content.map(_.trim).mkString("\n\n")))
        )

        projectModules ++ runtimeModules ++ sbtModules
    }
    Layouted(modules)
  }

  case class RawExpr(e: String)

  sealed trait Scope

  object Scope {

    case object ThisBuild extends Scope

    case object Project extends Scope

  }

  sealed trait SbtDslOp

  object SbtDslOp {

    final case class Append[T](v: T, scope: Scope = Scope.ThisBuild) extends SbtDslOp

    final case class Assign[T](v: T, scope: Scope = Scope.ThisBuild) extends SbtDslOp

  }

  def renderOp(pair: Tuple2[String, SbtDslOp]): String = {
    val key = pair._1
    val parts = pair._2 match {
      case SbtDslOp.Append(v, scope) =>
        v match {
          case s: Seq[_] =>
            Seq(key, renderScope(scope), "++=", renderValue(s))
          case o =>
            Seq(key, renderScope(scope), "+=", renderValue(o))
        }
      case SbtDslOp.Assign(v, scope) =>
        Seq(key, renderScope(scope), ":=", renderValue(v))
    }
    parts.mkString(" ")
  }

  def renderScope(scope: Scope): String = {
    scope match {
      case Scope.ThisBuild =>
        "in ThisBuild"
      case Scope.Project =>
        ""
    }
  }

  def renderValue(v: Any): String = {
    v match {
      case s: String =>
        s""""$s""""
      case b: Boolean =>
        b.toString
      case v: Number =>
        v.toString
      case o: Option[_] =>
        o match {
          case Some(value) =>
            s"Some(${renderValue(value)})"
          case None =>
            "None"
        }
      case u: BuildManifest.MFUrl =>
        s"url(${renderValue(u.url)})"
      case l: BuildManifest.License =>
        s"${renderValue(l.name)} -> ${renderValue(l.url)}"
      case s: Seq[_] =>
        s.map(renderValue).mkString("Seq(\n  ", ",\n  ", ")")
      case r: RawExpr =>
        r.e.trim
    }
  }


  private def asSbtModule(out: Seq[Module], did: DomainId) = {
    out.map {
      m =>
        val pid = projectId(did)
        m.copy(id = m.id.copy(path = Seq(pid, "src", "main", "scala") ++ m.id.path))
    }
  }

  private def projectId(did: DomainId): String = {
    val pkg = did.toPackage
    val parts = options.manifest.dropPackageHead match {
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
