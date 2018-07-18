package com.github.pshirshov.izumi.fundamentals.reflection

import com.github.pshirshov.izumi.fundamentals.platform.jvm.{CodePosition, SourceFilePosition}

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

final case class CodePositionMaterializer(get: CodePosition)

object CodePositionMaterializer {
  implicit def materialize: CodePositionMaterializer = macro getEnclosingPosition

  def apply()(implicit ev: CodePositionMaterializer): CodePositionMaterializer = ev

  def currentPosition(implicit ev: CodePositionMaterializer): CodePosition = ev.get

  def getEnclosingPosition(c: blackbox.Context): c.Expr[CodePositionMaterializer] = {
    import c.universe._
    val cp = getCodePosition(c)
    reify {
      CodePositionMaterializer(cp.splice)
    }
  }

  private def getCodePosition(c: blackbox.Context): c.Expr[CodePosition] = {
    import c.universe._
    getEnclosingPositionImpl(c) match {
      case CodePosition(SourceFilePosition(file, line), applicationPointId) =>
        reify {
          CodePosition.apply(
            SourceFilePosition(
              c.Expr[String](Literal(Constant(file))).splice
              , c.Expr[Int](Literal(Constant(line))).splice
            )
            , applicationPointId = c.Expr[String](Literal(Constant(applicationPointId))).splice
          )
        }
    }
  }

  private def getEnclosingPositionImpl(c: blackbox.Context): CodePosition = {
    CodePosition(
      SourceFilePosition(
        line = c.enclosingPosition.line
        , file = c.enclosingPosition.source.file.name
      )
      , applicationPointId = c.internal.enclosingOwner.fullName
    )
  }
}
