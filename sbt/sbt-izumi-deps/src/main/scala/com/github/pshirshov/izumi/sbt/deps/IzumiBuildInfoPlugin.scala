package com.github.pshirshov.izumi.sbt.deps

import sbt.Keys._
import sbt._

object IzumiBuildInfoPlugin extends AutoPlugin {

  object autoImport {

    def generateBuildInfo(packageName: String, objectName: String): Setting[_] =
      sourceGenerators in Compile += Def.task {
        val file =
          packageName
            .split('.')
            .foldLeft((sourceManaged in Compile).value)(_ / _) / s"$objectName.scala"

        val ver = version.value
        val group = (organization in LocalRootProject).value

        val allProjects = loadedBuild.value.allProjectRefs.map {
          case (ref, proj) =>
            val name = proj.id.replace("-", "_")

            // TODO: this would break in case of different orgs within same project!
            name -> s"""final val $name = group %% "${proj.id}" % version  """

        }.sortBy(_._1)

        val allProjectsR = allProjects.map(_._2)
        val allProjectsT = allProjects.map(_._1).map(s => s"""final val $s = R.$s % "test" """)
        val allProjectsCTT = allProjects.map(_._1).map(s => s"""final val $s = R.$s classifier "tests" """)
        val allProjectsTT = allProjects.map(_._1).map(s => s"""final val $s = CTT.$s % "test" """)


        IO.write(
          file,
          s"""package $packageName
             |
             |import sbt._
             |
             |object $objectName {
             |  final val version = "$ver"
             |  final val group = "$group"
             |
             |  object R {
             |    ${allProjectsR.mkString("\n    ")}
             |  }
             |
             |  object T {
             |    ${allProjectsT.mkString("\n    ")}
             |  }
             |
             |  object CTT {
             |    ${allProjectsCTT.mkString("\n    ")}
             |  }
             |
             |  object TT {
             |    ${allProjectsTT.mkString("\n    ")}
             |  }
             |}
             |""".stripMargin
        )

        Seq(file)
      }.taskValue
  }

}
