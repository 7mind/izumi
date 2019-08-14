package izumi.idealingua.compiler

import java.io.File

import izumi.fundamentals.platform.files.IzFiles
import izumi.idealingua.translator.IDLLanguage
import io.circe.Json
import io.circe.parser.parse
import io.circe.generic.auto._

sealed trait Credentials {
  def lang: IDLLanguage
}
class LangCredentials(override val lang: IDLLanguage) extends Credentials
case class ScalaCredentials(sbtRealm: String, sbtHost: String, sbtUser: String, sbtPassword: String,
                            sbtReleasesRepo: String, sbtSnapshotsRepo: String) extends LangCredentials(IDLLanguage.Scala)
case class TypescriptCredentials(npmRepo: String, npmUser: String, npmPassword: String, npmEmail: String) extends LangCredentials(IDLLanguage.Typescript)
case class GoCredentials(gitUser: String, gitEmail: String, gitRepoUrl: String, gitRepoName: String, gitPubKey: String) extends LangCredentials(IDLLanguage.Go)
case class CsharpCredentials(nugetRepo: String, nugetUser: String, nugetPassword: String) extends LangCredentials(IDLLanguage.CSharp)

class CredentialsReader(lang: IDLLanguage, file: File) {
  def read(overrides: Json): Either[Throwable, Credentials] = lang match {
    case IDLLanguage.Scala => read[ScalaCredentials](file, overrides)
    case IDLLanguage.Typescript => read[TypescriptCredentials](file, overrides)
    case IDLLanguage.Go => read[GoCredentials](file, overrides)
    case IDLLanguage.CSharp => read[CsharpCredentials](file, overrides)
  }

  def read[T <: Credentials](file: File, overrides: Json)(implicit d: io.circe.Decoder[T]): Either[Throwable, Credentials] = for {
    json <- parse(IzFiles.readString(file))
    creds <- json.deepMerge(overrides).as[T]
  } yield creds.asInstanceOf[Credentials]
}
