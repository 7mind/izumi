package izumi.idealingua.translator

sealed trait IDLLanguage

object IDLLanguage {
  case object Scala extends IDLLanguage {
    override val toString: String = "scala"
  }
  case object Go extends IDLLanguage {
    override val toString: String = "go"
  }
  case object Typescript extends IDLLanguage {
    override val toString: String = "typescript"
  }
  case object CSharp extends IDLLanguage {
    override val toString: String = "csharp"
  }

  def parse(s: String): IDLLanguage = {
    s.trim.toLowerCase match {
      case Scala.toString  =>
        Scala
      case Go.toString  =>
        Go
      case Typescript.toString  =>
        Typescript
      case CSharp.toString  =>
        CSharp
    }
  }
}
