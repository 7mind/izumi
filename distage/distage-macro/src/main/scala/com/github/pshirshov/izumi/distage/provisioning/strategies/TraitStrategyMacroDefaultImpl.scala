package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.functions.WrappedFunction
import com.github.pshirshov.izumi.distage.model.reflection.SymbolIntrospector
import com.github.pshirshov.izumi.distage.model.reflection.universe.MacroUniverse
import com.github.pshirshov.izumi.distage.reflection.SymbolIntrospectorDefaultImpl

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

trait TraitStrategyMacroDefaultImpl {
  self: TraitStrategyMacro =>

  def mkWrappedTraitConstructor[T]: WrappedFunction[T] = macro TraitStrategyMacroDefaultImplImpl.mkWrappedTraitConstructorMacro[T]

  @inline
  // reason for this is simply IDEA flipping out on [T: c.WeakTypeTag]
  override def mkWrappedTraitConstructorMacro[T: blackbox.Context#WeakTypeTag](c: blackbox.Context): c.Expr[WrappedFunction[T]] =
    TraitStrategyMacroDefaultImplImpl.mkWrappedTraitConstructorMacro[T](c)
}

object TraitStrategyMacroDefaultImpl
  extends TraitStrategyMacroDefaultImpl
    with TraitStrategyMacro

// TODO: Preserve annotations to support IDs

private[strategies] object TraitStrategyMacroDefaultImplImpl {

  def mkWrappedTraitConstructorMacro[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[WrappedFunction[T]] = {
    import c.universe._

    val macroUniverse = MacroUniverse(c)
    val symbolIntrospector = SymbolIntrospectorDefaultImpl.Macro.instance(macroUniverse)

    val targetType = weakTypeOf[T]

    val (wireArgs, wireMethods) = targetType
      .members
      .sorted
      .collect(makeDeclsAndConstructorArgs(c)(macroUniverse)(symbolIntrospector)(targetType))
      .unzip

    val instantiate = if (wireMethods.isEmpty)
      q"new $targetType {}"
    else
      q"new $targetType { ..$wireMethods }"

    val expr =
      c.Expr {
        q"""
      {
      ${if (wireArgs.nonEmpty)
          q"def traitConstructor(..$wireArgs): $targetType = ($instantiate).asInstanceOf[$targetType]"
        else
          q"def traitConstructor: $targetType = ($instantiate).asInstanceOf[$targetType]"
      }

      (traitConstructor _)
      }
      """
      }

    val wrappedFunction = typeOf[WrappedFunction[_]].typeSymbol

    val res = c.Expr[WrappedFunction[T]] {
      q"""
          {
          val ctor = $expr

          // trigger implicit conversion
          _root_.scala.Predef.identity[$wrappedFunction[$targetType]](ctor)
          }
       """
    }
    c.info(c.enclosingPosition, s"Syntax tree of trait $targetType:\n$res", force = false)
    res
  }

  private def makeDeclsAndConstructorArgs
    (c: blackbox.Context)
      (macroUniverse: MacroUniverse[c.universe.type])
        (symbolIntrospector: SymbolIntrospector.Macro[macroUniverse.type])
          (targetType: c.universe.Type)
          : PartialFunction[c.universe.Symbol, (c.universe.Tree, c.universe.Tree)] = {
    case d if symbolIntrospector.isWireableMethod(macroUniverse.SafeType(targetType), d) =>
      import c.universe._
      val methodName = d.asMethod.name.toTermName
      val argName = c.freshName(methodName)
      val tpe = d.typeSignature.finalResultType

      (q"$argName: $tpe", q"override val $methodName: $tpe = $argName")
  }
}


/*
import scala.annotation.StaticAnnotation
import language.experimental.macros

class body(tree: Any) extends StaticAnnotation

trait Macros{
  import c.universe._

  def selFieldImpl = {
    val field = c.macroApplication.symbol
    val bodyAnn = field.annotations.filter(_.tpe <:< typeOf[body]).head
    bodyAnn.scalaArgs.head
  }

  def mkObjectImpl(xs: c.Tree*) = {
    val kvps = xs.toList map { case q"${_}(${Literal(Constant(name: String))}).->[${_}]($value)" => name -> value }
    val fields = kvps map { case (k, v) => q"@body($v) def ${TermName(k)} = macro Macros.selFieldImpl" }
    q"class Workaround { ..$fields }; new Workaround{}"
  }
}

object mkObject {
  def apply(xs: Any*) = macro Macros.mkObjectImpl
}

object Test {
  def main(args: Array[String]) = {
    val foo = mkObject("x" -> "2", "y" -> 3)
    println(foo.x)
    println(foo.y)
    // println(foo.z) => will result in a compilation error
  }
}

 */

//      q"""{
//         val universe: scala.reflect.runtime.universe.type = scala.reflect.runtime.universe
//         import universe._
//
//         class Workaround(hey: Int) extends $className {
//         ..$m
//         }
//
//         x = new Workaround(5)
//
//        (${reify(c.Expr(q"weakTypeTag[Workaround]").splice)}, x)
//        }"""}
//          new $className {  ..$m } """}
