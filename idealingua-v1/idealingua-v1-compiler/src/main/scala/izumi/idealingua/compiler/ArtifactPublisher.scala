package izumi.idealingua.compiler

import java.nio.file._
import java.time.ZonedDateTime

import izumi.fundamentals.platform.files.IzFiles
import izumi.idealingua.model.publishing.BuildManifest
import izumi.idealingua.model.publishing.manifests.GoLangBuildManifest
import izumi.idealingua.translator.IDLLanguage

import scala.sys.process._
import scala.util.Try
import scala.jdk.CollectionConverters._


class ArtifactPublisher(targetDir: Path, lang: IDLLanguage, creds: Credentials, manifest: BuildManifest) {
  private val log: CompilerLog = CompilerLog.Default

  def publish(): Either[Throwable, Unit] = (creds, lang, manifest) match {
    case (c: ScalaCredentials, IDLLanguage.Scala, _) => publishScala(targetDir, c)
    case (c: TypescriptCredentials, IDLLanguage.Typescript, _) => publishTypescript(targetDir, c)
    case (c: GoCredentials, IDLLanguage.Go, m: GoLangBuildManifest) => publishGo(targetDir, c, m)
    case (c: CsharpCredentials, IDLLanguage.CSharp, _) => publishCsharp(targetDir, c)
    case (c, l, _) if c.lang != l =>
      Left(new IllegalArgumentException("Language and credentials type didn't match. " +
        s"Got credentials for $l, expect for ${c.lang}"))
  }

  private def publishScala(targetDir: Path, creds: ScalaCredentials): Either[Throwable, Unit] = Try {
    log.log("Prepare to package Scala sources")
    Process(
      "sbt clean package",
      targetDir.toFile
    ).lineStream.foreach(log.log)

    log.log("Writing credentials file to ")
    val buildFile = targetDir.toAbsolutePath.resolve("build.sbt")
    val sbtCredsFile = targetDir.toAbsolutePath.resolve(".credentials")

    val credsLines = Seq(
      "\n",
      // TODO: Gigahorse apears to be cause of `Too many follow-up requests: 21` exception during publishing
      "updateOptions in ThisBuild := updateOptions.value.withGigahorse(false)",
      "\n",
      s"""credentials += Credentials(Path("${sbtCredsFile.toAbsolutePath.toString}").asFile)""",
      "\n",
      s"""
        |publishTo in ThisBuild := {
        |  if (isSnapshot.value)
        |    Some("snapshots" at "${creds.sbtSnapshotsRepo}")
        |  else
        |    Some("releases"  at "${creds.sbtReleasesRepo}")
        |}
      """.stripMargin
    )

    Files.write(buildFile, credsLines.asJava, StandardOpenOption.WRITE, StandardOpenOption.APPEND)

    Files.write(sbtCredsFile, Seq[String](
      s"realm=${creds.sbtRealm}",
      s"host=${creds.sbtHost}",
      s"user=${creds.sbtUser}",
      s"password=${creds.sbtPassword}"
    ).asJava)

    Process(
      "sbt publish",
      targetDir.toFile
    ).lineStream.foreach(log.log)
  }.toEither

  private def publishTypescript(targetDir: Path, creds: TypescriptCredentials): Either[Throwable, Unit] = Try {
    log.log("Prepare to package Typescript sources")

    val packagesDir = Files.list(targetDir.resolve("packages")).filter(_.toFile.isDirectory).iterator().asScala.toSeq.head
    val credsFile = Paths.get(System.getProperty("user.home")).resolve("~/.npmrc")
    val repoName = creds.npmRepo.replaceAll("http://", "").replaceAll("https://", "")
    val scope = packagesDir.getFileName

    log.log(s"Writing credentials in ${credsFile.toAbsolutePath.getFileName}")

    val scriptLines =
      List(
        Seq("echo", s"Setting NPM registry for scope $scope to $repoName using user & _password method..."),
        Seq("npm", "config", "set", s"$scope:registry", s"${creds.npmRepo}"),
        Seq("npm", "config", "set", s"//$repoName:email", s"${creds.npmEmail}"),
        Seq("npm", "config", "set", s"//$repoName:always-auth", "true"),
        Seq("npm", "config", "set", s"//$repoName:username", s"${creds.npmUser}"),
        Seq("npm", "config", "set", s"//$repoName:_password", (Seq("echo", "-n", s"${creds.npmPassword}") #| Seq("openssl", "base64")).!!),
      )

    scriptLines.foreach(s => Process(s, targetDir.toFile).lineStream.foreach(log.log))

    log.log("Publishing NPM packages")

    log.log("Yarn installing")
    Process(
      "yarn install",
      targetDir.toFile
    ).lineStream.foreach(log.log)

    log.log("Yarn building")
    Process(
      "yarn build",
      targetDir.toFile
    ).lineStream.foreach(log.log)

    Files.list(targetDir.resolve("dist")).filter(_.toFile.isDirectory).iterator().asScala.foreach { module =>
      val cmd = s"npm publish --force --registry ${creds.npmRepo} ${module.toAbsolutePath.toString}"
      log.log(s"Publish ${module.getFileName}. Cmd: `$cmd`")
      Files.copy(packagesDir.resolve(s"${module.getFileName}/package.json"), module.resolve("package.json"))
      Process(cmd, targetDir.toFile).lineStream.foreach(log.log)
    }
  }.toEither

  private def publishCsharp(targetDir: Path, creds: CsharpCredentials): Either[Throwable, Unit] = Try {
    val nuspecDir = targetDir.resolve("nuspec")
    val nuspecFile = nuspecDir.toFile

    log.log("Prepare to package C# sources")

    log.log("Preparing credentials")
    Process(
      s"nuget sources Add -Name IzumiPublishSource -Source ${creds.nugetRepo}", nuspecFile
    ).#||("true").lineStream.foreach(log.log)

    Process(
      s"nuget setapikey ${creds.nugetUser}:${creds.nugetPassword} -Source IzumiPublishSource", nuspecFile
    ).lineStream.foreach(log.log)

    log.log("Publishing")
    Files.list(nuspecDir).filter(_.getFileName.toString.endsWith(".nuspec")).iterator().asScala.foreach { module =>
      Try(
        Process(
          s"nuget pack ${module.getFileName.toString}", nuspecFile
        ).lineStream.foreach(log.log)
      )
    }

    IzFiles.walk(nuspecFile).filter(_.getFileName.toString.endsWith(".nupkg")).foreach { pack =>
      Process(
        s"nuget push ${pack.getFileName.toString} -Source IzumiPublishSource", nuspecFile
      ).lineStream.foreach(log.log)
    }
  }.toEither

  private def publishGo(targetDir: Path, creds: GoCredentials, manifest: GoLangBuildManifest): Either[Throwable, Unit] = Try {
    log.log("Prepare to package GoLang sources")

    val env = "GOPATH" -> s"${targetDir.toAbsolutePath.toString}"
    IzFiles.recreateDir(targetDir.resolve("src"))

    Files.move(targetDir.resolve("github.com"), targetDir.resolve("src/github.com"), StandardCopyOption.REPLACE_EXISTING)

    Process(
      "go get github.com/gorilla/websocket", targetDir.toFile, env
    ).lineStream.foreach(log.log)

    if (manifest.enableTesting) {
      log.log("Testing")
      Process(
        "go test ./...", targetDir.resolve("src").toFile, env
      ).lineStream.foreach(log.log)
      log.log("Testing - OK")
    } else {
      log.log("Testing is disabled. Skipping.")
    }

    log.log("Publishing to github repo")

    log.log("Seting up Git")
    val pubKey = targetDir.resolve("go-key.pub")
    Files.write(pubKey, Seq(creds.gitPubKey).asJava)

    Process(Seq("git", "config", "--global", "--replace-all", "user.name", creds.gitUser)).lineStream.foreach(log.log)
    Process(Seq("git", "config", "--global", "--replace-all", "user.email", creds.gitEmail)).lineStream.foreach(println)
    Process(Seq("git", "config", "--global", "--replace-all", "core.sshCommand", s"ssh -i ${pubKey.toAbsolutePath.toString} -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no")).lineStream.foreach(println)

    Process(
      "git config --global --list", targetDir.toFile
    ).lineStream.foreach(log.log)

    Process(
      s"git clone ${creds.gitRepoUrl}", targetDir.toFile
    ).lineStream.foreach(log.log)

    Files.list(targetDir.resolve(creds.gitRepoName)).iterator().asScala
      .filter(_.getFileName.toString.charAt(0) != '.')
      .foreach { path =>
        IzFiles.removeDir(path)
      }

    Files.list(targetDir.resolve("src").resolve(manifest.repository.repository)).iterator().asScala.foreach { srcDir =>
      Files.move(srcDir, targetDir.resolve(creds.gitRepoName).resolve(srcDir.getFileName.toString))
    }

    Files.write(targetDir.resolve(creds.gitRepoName).resolve(".timestamp"), Seq(
      ZonedDateTime.now().toString
    ).asJava)
    Files.write(targetDir.resolve(creds.gitRepoName).resolve("README.md"), Seq(
      s"# ${creds.gitRepoName}",
      s"Auto-generated golang apis, ${manifest.common.version.toString}"
    ).asJava)

    Process(
      "git add .", targetDir.resolve(creds.gitRepoName).toFile
    ).lineStream.foreach(log.log)

    log.log(s"Git commit: 'golang-api-update,version=${manifest.common.version}'")
    Process(
      s"""git commit --no-edit -am 'golang-api-update,version=${manifest.common.version}'""", targetDir.resolve(creds.gitRepoName).toFile
    ).lineStream.foreach(log.log)

    log.log(s"Setting git tag: v${manifest.common.version.toString}")
    Process(
      s"git tag -f v${manifest.common.version.toString}", targetDir.resolve(creds.gitRepoName).toFile
    ).lineStream.foreach(log.log)

    log.log("Git push")
    Process(
      "git push --all -f", targetDir.resolve(creds.gitRepoName).toFile
    ).lineStream.foreach(log.log)
    Process(
      "git push --tags -f", targetDir.resolve(creds.gitRepoName).toFile
    ).lineStream.foreach(log.log)
  }.toEither
}
