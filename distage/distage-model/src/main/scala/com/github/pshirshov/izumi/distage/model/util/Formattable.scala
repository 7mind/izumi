package com.github.pshirshov.izumi.distage.model.util

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._

import scala.language.implicitConversions

trait Formattable {
  def format: Format
}

sealed trait Arg[V] {
  val value: V
  override def toString: String = value.toString
}
case class DIKeyArg(value: DIKey) extends Arg[DIKey]
case class ClassArg(value: Class[_]) extends Arg[Class[_]]
case class FormatArg(value: Format) extends Arg[Format]
case class FormatSeqArg(value: Seq[Format]) extends Arg[Format]
case class AnyArg(value: Any) extends Arg[Any]

case class Format(format: String, args: Arg[_]*) {
  def render(): String = {
    String.format(format, args.map(_.toString): _*)
  }
}

object Format {
  implicit def toClassNameArg(value: DIKey): Arg[DIKey] = DIKeyArg(value)
  implicit def toAnyArg(value: Any): Arg[Any] = AnyArg(value)
  implicit def toClassArg(value: Class[_]): Arg[Class[_]] = ClassArg(value)
  implicit def toFormatArg(value: Format): Arg[Format] = FormatArg(value)
  implicit def toFormatSeqArg(value: Seq[Format]): Arg[Format] = FormatSeqArg(value)
}
