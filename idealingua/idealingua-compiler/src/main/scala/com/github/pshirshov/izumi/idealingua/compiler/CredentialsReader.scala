package com.github.pshirshov.izumi.idealingua.compiler

import java.io.File

import com.github.pshirshov.izumi.fundamentals.platform.files.IzFiles
import com.github.pshirshov.izumi.idealingua.translator.IDLLanguage
import io.circe.parser.parse
import io.circe.generic.auto._

sealed trait Credentials {
  def lang: IDLLanguage
}
class LangCredentials(override val lang: IDLLanguage) extends Credentials
case class ScalaCredentials(realm: String, host: String, user: String, password: String,
                            releasesRepo: String, snapshotsRepo: String) extends LangCredentials(IDLLanguage.Scala)
case class TypescriptCredentials(npmRepo: String, user: String, password: String, email: String) extends LangCredentials(IDLLanguage.Typescript)
case class GoCredentials(gitUser: String, gitEmail: String, gitRepoUrl: String, gitRepoName: String, gitPubKey: String) extends LangCredentials(IDLLanguage.Go)
case class CsharpCredentials(nugetRepo: String, user: String, password: String) extends LangCredentials(IDLLanguage.CSharp)

class CredentialsReader(lang: IDLLanguage, file: File) {
  def read(): Either[Throwable, Credentials] = lang match {
    case IDLLanguage.Scala => read[ScalaCredentials](file)
    case IDLLanguage.Typescript => read[TypescriptCredentials](file)
    case IDLLanguage.Go => read[GoCredentials](file)
    case IDLLanguage.CSharp => read[CsharpCredentials](file)
  }

  def read[T <: Credentials](file: File)(implicit d: io.circe.Decoder[T]): Either[Throwable, Credentials] = for {
    json <- parse(IzFiles.readString(file))
    creds <- json.as[T]
  } yield creds.asInstanceOf[Credentials]
}
