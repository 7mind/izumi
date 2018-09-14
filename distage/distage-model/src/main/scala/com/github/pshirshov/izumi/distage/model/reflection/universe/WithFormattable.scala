package com.github.pshirshov.izumi.distage.model.reflection.universe

import com.github.pshirshov.izumi.distage.model.references.WithDIKey

import scala.language.implicitConversions

trait WithFormattable {
  this: DIUniverseBase with WithDIKey =>

  sealed trait FormatArg
  case class AnyRefArg(value: AnyRef) extends FormatArg
  case class DIKeyArg(value: DIKey) extends FormatArg

  case class Format(format: String, args: FormatArg*) {
    def render(): String = String.format(format, args)
  }

  object Format {
    implicit def toClassNameArg(value: DIKey): FormatArg = DIKeyArg(value)
    implicit def toAnyRef(value: AnyRef): FormatArg = AnyRefArg(value)
  }
}
