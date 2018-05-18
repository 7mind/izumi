// we need this to be copied here for build bootstrapping
libraryDependencies ++= Seq(
  "org.scala-sbt" % "sbt" % sbtVersion.value
)

sbtPlugin := true

def generateBuildInfo(packageName: String, objectName: String): Setting[_] =
  sourceGenerators in Compile += Def.task {
    val file =
      packageName
        .split('.')
        .foldLeft((sourceManaged in Compile).value)(_ / _) / s"$objectName.scala"

    IO.write(
      file,
      s"""package $packageName
         |
         |object $objectName {
         |  final val version = "${version.value}"
         |}
         |""".stripMargin
    )

    Seq(file)
  }.taskValue

generateBuildInfo("com.github.pshirshov.izumi.sbt.deps", "Izumi")
