package com.github.pshirshov.izumi.idealingua.compiler

import java.nio.file.{Files, Path, StandardOpenOption}

import com.github.pshirshov.izumi.idealingua.translator.IDLLanguage

import scala.sys.process.Process
import scala.util.Try
import scala.collection.JavaConverters._


class ArtifactPublisher(path: Path, lang: IDLLanguage, creds: Credentials) {
  private val log: CompilerLog = CompilerLog.Default

  def publish(): Either[Throwable, Unit] = (creds, lang) match {
    case (c: ScalaCredentials, IDLLanguage.Scala) => publishScala(path, c)
    case (c: TypescriptCredentials, IDLLanguage.Typescript) => ???
    case (c: GoCredentials, IDLLanguage.Go) => ???
    case (c: CsharpCredentials, IDLLanguage.CSharp) => ???
    case (c, l) if c.lang != l =>
      Left(new IllegalArgumentException(s"Language and credentials type didn't match. " +
        s"Got credentials for $l, expect for ${c.lang}"))
  }

  private def publishScala(path: Path, creds: ScalaCredentials): Either[Throwable, Unit] = Try {
    log.log("Prepare to package Scala sources")
    Process(
      "sbt clean package",
      path.toFile
    ).lineStream.foreach(log.log)

    log.log("Writing credentials file to ")
    val buildFile = path.toAbsolutePath.resolve("build.sbt")
    val sbtCredsFile = path.toAbsolutePath.resolve(".credentials")

    val credsLines = Seq(
      "\n",
      s"""credentials += Credentials(Path("${sbtCredsFile.toAbsolutePath.toString}").asFile)""",
      "\n",
      s"""
        |publishTo := {
        |  if (isSnapshot.value)
        |    Some("snapshots" at "${creds.snapshotsRepo}")
        |  else
        |    Some("releases"  at "${creds.releasesRepo}")
        |}
      """.stripMargin
    )

    Files.write(buildFile, credsLines.asJava, StandardOpenOption.WRITE, StandardOpenOption.APPEND)

    Files.write(sbtCredsFile, Seq[String](
      s"realm=${creds.realm}",
      s"host=${creds.host}",
      s"user=${creds.user}",
      s"password=${creds.password}"
    ).asJava)

    Process(
      "sbt publish",
      path.toFile
    ).lineStream.foreach(log.log)
  }.toEither
}
