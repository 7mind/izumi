package com.github.pshirshov.izumi.idealingua.translator

sealed trait IDLLanguage

object IDLLanguage {
  case object Scala extends IDLLanguage
  case object Go extends IDLLanguage
  case object Typescript extends IDLLanguage
  case object UnityCSharp extends IDLLanguage
}
