package izumi.sbt.deps

import sbt.Keys._
import sbt.{Def, _}

case class ExportedModule(ref: ProjectRef, safeId: String, groupId: String, artifactId: String, version: String)

case class ImportedDep(safeId: String, vname: String, verstr: String, depstr: String)

case class ImportedDeps(safeId: String, deps: Seq[ImportedDep])

object IzumiBuildInfoPlugin extends AutoPlugin {

  implicit class StringExt(s: String) {
    def quoted: String = "\"" + s + "\""

    def safe: String = {
      s
        .replace("-", "_")
        .replace(".", "_")
    }

    def shift(delta: Int): String = {
      val shift = " " * delta
      s.split('\n').map(s => s"$shift$s").mkString("\n")
    }
  }

  private def choose(global: String, local: String, name: String): String = {
    if (global == local) {
      name
    } else {
      local.quoted
    }
  }

  def formatModuleId(m: ModuleID, verstr: String): String = {
    import sbt.librarymanagement._

    val sep = m.crossVersion match {
      case _: Binary =>
        "%%"
      case _: Disabled =>
        "%"

      // I'm not sure if it's a good idea, maybe better to throw?..
      case _: Full =>
        "% /* CONSTANT */"
      case _: Constant =>
        "% /* CONSTANT */"
      case _: Patch =>
        "% /* PATCH */"
    }
    // TODO: should we support classifiers with m.explicitArtifacts ?

    Seq(m.organization.quoted, sep, m.name.quoted, "%", verstr).mkString(" ")
  }


  def generateBuildInfo(packageName: String, objectName: String): Def.Initialize[Task[Seq[File]]] = {
    val buildDepMap = {
      Def.taskDyn {
        val refs = loadedBuild.value.allProjectRefs

        val tt = refs.map(_._1).map {
          ref =>
            libraryDependencies.all(ScopeFilter(inProjects(ref)))
              .zipWith(Def.setting(ref)) { case (a, b) => b -> a }
        }

        Def.task {
          tt
        }
      }
    }

    val buildGroupIdMap = {
      Def.taskDyn {
        val refs = loadedBuild.value.allProjectRefs

        val tt = refs.map(_._1).map {
          ref =>
            organization.all(ScopeFilter(inProjects(ref)))
              .zipWith(Def.setting(ref)) { case (a, b) => b -> a }
        }

        Def.task {
          tt
        }
      }
    }

    val buildVersionMap = {
      Def.taskDyn {
        val refs = loadedBuild.value.allProjectRefs

        val tt = refs.map(_._1).map {
          ref =>
            version.all(ScopeFilter(inProjects(ref)))
              .zipWith(Def.setting(ref)) { case (a, b) => b -> a }
        }

        Def.task {
          tt
        }
      }
    }

    Def.task {
      val file =
        packageName
          .split('.')
          .foldLeft((sourceManaged in Compile).value)(_ / _) / s"$objectName.scala"

      val sd = settingsData.in(Global).value
      val ver = (version in LocalRootProject).value
      val group = (organization in LocalRootProject).value

      val allProjectRefs = loadedBuild.value.allProjectRefs

      val depmap = buildDepMap.value.map(_.evaluate(sd)).toMap
      val vermap = buildVersionMap.value.map(_.evaluate(sd)).toMap
      val groupmap = buildGroupIdMap.value.map(_.evaluate(sd)).toMap


      val allProjects = allProjectRefs.map {
        case (ref, proj) =>
          val name = proj.id.safe
          val pgid = choose(group, groupmap(ref).head, "group")
          val pver = choose(ver, vermap(ref).head, "version")

          ExportedModule(ref, name, pgid, proj.id.quoted, pver)
      }.sortBy(_.safeId)

      val allProjectsR = allProjects.map(m => s"""final val ${m.safeId} = ${m.groupId} %% ${m.artifactId} % ${m.version}""")
      val allProjectsT = allProjects.map(m => s"""final val ${m.safeId} = R.${m.safeId} % Test """)
      val allProjectsCTT = allProjects.map(m => s"""final val ${m.safeId} = R.${m.safeId} classifier "tests" """)
      val allProjectsTT = allProjects.map(m => s"""final val ${m.safeId} = TSR.${m.safeId} % Test """)



      val imports = allProjects.map {
        p =>
          val deps = depmap(p.ref).flatten.map {
            d =>
              val name = (d.organization + "_" + d.name).safe
              val vname = name + "_version"
              ImportedDep(name, vname, d.revision.quoted, formatModuleId(d, vname))
          }

          ImportedDeps(p.safeId, deps)
      }.map {
        d =>
          val uniqEntries = d.deps.groupBy(_.safeId).mapValues(_.head).values.toSeq.sortBy(_.safeId)

          val vals = uniqEntries.map {
            v =>
              s""" val ${v.safeId} = ${v.depstr} """
          }

          val vers = uniqEntries.map {
            v =>
              s""" val ${v.vname} = ${v.verstr} """
          }

          s"""object ${d.safeId} {
             |${vers.mkString("\n").shift(2)}
             |${vals.mkString("\n").shift(2)}
             |}
           """.stripMargin
      }


      IO.write(
        file,
        s"""package $packageName
           |import sbt._
           |
           |object $objectName {
           |  final val version = "$ver"
           |  final val group = "$group"
           |  final val scalaVersion = "${scalaVersion.value}"
           |  final val sbtVersion = "${sbtVersion.value}"
           |
           |  /**
           |   * Runtime artifacts
           |   */
           |  object R {
           |    ${allProjectsR.mkString("\n    ")}
           |  }
           |
           |  /**
           |   * Runtime artifacts for test scope
           |   */
           |  object T {
           |    ${allProjectsT.mkString("\n    ")}
           |  }
           |
           |  /**
           |   * Test artifacts
           |   */
           |  object TSR {
           |    ${allProjectsCTT.mkString("\n    ")}
           |  }
           |
           |  /**
           |   * Test artifacts for test scope
           |   */
           |  object TST {
           |    ${allProjectsTT.mkString("\n    ")}
           |  }
           |
           |  object Deps {
           |  ${imports.mkString("\n").shift(2)}
           |  }
           |}
           |
           |object ${objectName}Plugin extends AutoPlugin {
           |  override def trigger = allRequirements
           |
           |  object autoImport {
           |    val $objectName: $packageName.$objectName.type = $packageName.$objectName
           |  }
           |}
           |
         """.stripMargin
      )

      Seq(file)
    }
  }

  object autoImport {
    def withBuildInfo(packageName: String, objectName: String): Seq[Setting[_]] = Seq(
      sbtPlugin := true
      , sourceGenerators in Compile += generateBuildInfo(packageName, objectName).taskValue
    )
  }

}
