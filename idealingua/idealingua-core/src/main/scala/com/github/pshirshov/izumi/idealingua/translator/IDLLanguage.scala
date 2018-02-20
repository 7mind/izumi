package com.github.pshirshov.izumi.idealingua.translator

sealed trait IDLLanguage

object IDLLanguage {
  case object Scala extends IDLLanguage {
    override def toString: String = "scala"
  }
  case object Go extends IDLLanguage {
    override def toString: String = "go"
  }
  case object Typescript extends IDLLanguage {
    override def toString: String = "typescript"
  }
  case object UnityCSharp extends IDLLanguage {
    override def toString: String = "csharp-unity"
  }
}
