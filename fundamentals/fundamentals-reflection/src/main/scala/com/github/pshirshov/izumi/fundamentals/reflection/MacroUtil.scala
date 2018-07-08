package com.github.pshirshov.izumi.fundamentals.reflection

import com.github.pshirshov.izumi.fundamentals.platform.console.{AbstractStringSink, TrivialLogger}

import scala.reflect.ClassTag
import scala.reflect.macros.blackbox

import scala.language.experimental.macros

object MacroUtil {

  class MacroSink(c: blackbox.Context) extends AbstractStringSink {
    override def flush(value: => String): Unit =
      c.info(c.enclosingPosition, value, force = true)
  }

  def mkLogger[T: ClassTag](c: blackbox.Context): TrivialLogger =
    TrivialLogger.make[T]("izumi.distage.debug.macro", sink = new MacroSink(c))

  final case class EnclosingPosition(line: Int, file: String, applicationPointId: String)

  object EnclosingPosition {
    implicit def get: EnclosingPosition = macro getEnclosingPosition

    def getEnclosingPosition(c: blackbox.Context): c.Expr[EnclosingPosition] = {
      import c.universe._

      val (line, file, applicationPointId) = getEnclosingPositionExprs(c)

      reify {
        EnclosingPosition(line.splice, file.splice, applicationPointId.splice)
      }
    }

    def getEnclosingPositionExprs(c: blackbox.Context): (c.Expr[Int], c.Expr[String], c.Expr[String]) = {
      import c.universe._

      getEnclosingPositionImpl(c) match {
        case EnclosingPosition(line, file, applicationPointId) =>
          ( c.Expr[Int](Literal(Constant(line)))
          , c.Expr[String](Literal(Constant(file)))
          , c.Expr[String](Literal(Constant(applicationPointId)))
          )
      }
    }

    def getEnclosingPositionImpl(c: blackbox.Context): EnclosingPosition = {
      EnclosingPosition(
        line = c.enclosingPosition.line
        , file = c.enclosingPosition.source.file.name
        , applicationPointId = c.internal.enclosingOwner.fullName
      )
    }
  }

}
