package com.github.pshirshov.izumi.idealingua.translator.togolang

import com.github.pshirshov.izumi.idealingua.model.JavaType

import scala.meta.{Init, Term, Type}

trait GoLangType {
  def render(): String
}

trait GoLangPrimitive extends GoLangType

case class Bool() extends GoLangPrimitive {
  def render() = "bool"
}

case class StringType() extends GoLangPrimitive {
  def render() = "string"
}

trait GoLangIntegral extends GoLangPrimitive

case class Byte() extends GoLangIntegral {
  def render() = "byte"
}

case class Int8() extends GoLangIntegral {
  def render() = "int8"
}

case class Int16() extends GoLangIntegral {
  def render() = "int16"
}

case class Int32() extends GoLangIntegral {
  def render() = "int32"
}

case class Int64() extends GoLangIntegral {
  def render() = "int64"
}

trait GoLangFloat extends GoLangPrimitive

case class Float32() extends GoLangFloat {
  def render() = "float32"
}

case class Float64() extends GoLangFloat {
  def render() = "float64"
}

trait GoLangRefType extends GoLangType

case class Time() extends GoLangRefType {
  def render() = "time.Time"
}

trait GoLangGenericType extends GoLangRefType

case class ListType(of: Option[GoLangType] = None, size: Option[Int] = None) extends GoLangGenericType {
  def render(): String = {
    val sizeStr = size.map(_.toString).getOrElse("")
    of match {
      case Some(t) => s"[$sizeStr]${t.render()}"
      case None => s"[$sizeStr]interface{}"
    }
  }
}

case class MapType(of: Option[(GoLangType, GoLangType)]) extends GoLangGenericType {
  def render(): String = of match {
    case Some((keyT, valueT)) => s"map[${keyT.render()}]${valueT.render()}"
    case None => s"map[interface{}]interface{}"
  }
}

case class GoLangInterface(name: String) extends GoLangRefType {
  override def render(): String = name
}
