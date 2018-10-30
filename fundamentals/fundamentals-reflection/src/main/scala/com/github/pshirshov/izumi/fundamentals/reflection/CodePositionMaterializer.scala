package com.github.pshirshov.izumi.fundamentals.reflection

import com.github.pshirshov.izumi.fundamentals.platform.jvm.{CodePosition, SourceFilePosition}

import scala.annotation.tailrec
import scala.collection.mutable
import scala.language.experimental.macros
import scala.reflect.macros.blackbox

final case class CodePositionMaterializer(get: CodePosition)

object CodePositionMaterializer {
  implicit def materialize: CodePositionMaterializer = macro getEnclosingPosition

  def apply()(implicit ev: CodePositionMaterializer): CodePositionMaterializer = ev

  def codePosition(implicit ev: CodePositionMaterializer): CodePosition = ev.get

  def sourcePosition(implicit ev: CodePositionMaterializer): SourceFilePosition = ev.get.position

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

    def goodSymbol(s: c.Symbol): Boolean = {
      val name = s.name.toString
      !name.startsWith("$") && !name.startsWith("<")
    }

    @tailrec
    def rec(s: c.Symbol, st: mutable.ArrayBuffer[c.Symbol]): Unit = {
      st.prepend(s)
      s.owner match {
        case c.universe.NoSymbol =>
        case o =>
          rec(o, st)

      }
    }

    val ownerName = c.internal.enclosingOwner.fullName

    val st = mutable.ArrayBuffer[c.Symbol]()
    rec(c.internal.enclosingOwner, st)

    //val normalizedName = ownerName.split('.').takeWhile(s => !s.contains('$') && !s.contains('<')).mkString(".")

    val normalizedName = st
      .tail
      .map {
        case s if s.isPackage => s.name
        case s if goodSymbol(s) => s.name
        case s => if (s.isClass) {
          s.asClass.baseClasses.find(goodSymbol).map(_.name).getOrElse(s.pos.line)
        } else {
          s.pos.line
        }
      }
      .map(_.toString.trim)
      .mkString(".")

    CodePosition(
      SourceFilePosition(
        line = c.enclosingPosition.line
        , file = c.enclosingPosition.source.file.name
      )
      , applicationPointId = normalizedName
    )
  }
}
