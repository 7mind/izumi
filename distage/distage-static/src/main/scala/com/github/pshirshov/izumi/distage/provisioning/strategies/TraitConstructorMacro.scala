package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.functions.WrappedFunction.DIKeyWrappedFunction
import com.github.pshirshov.izumi.distage.model.reflection.universe.StaticDIUniverse
import com.github.pshirshov.izumi.distage.provisioning.TraitConstructor
import com.github.pshirshov.izumi.distage.reflection.{DependencyKeyProviderDefaultImpl, ReflectionProviderDefaultImpl, SymbolIntrospectorDefaultImpl}
import com.github.pshirshov.izumi.fundamentals.reflection.MacroUtil

import scala.reflect.macros.blackbox

object TraitConstructorMacro {

  def mkTraitConstructor[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[TraitConstructor[T]] = {
    import c.universe._

    val macroUniverse = StaticDIUniverse(c)
    import macroUniverse._
    import macroUniverse.Wiring._
    import macroUniverse.Association._

    val keyProvider = DependencyKeyProviderDefaultImpl.Static.instance(macroUniverse)
    val symbolIntrospector = SymbolIntrospectorDefaultImpl.Static.instance(macroUniverse)
    val reflectionProvider = ReflectionProviderDefaultImpl.Static.instance(macroUniverse)(keyProvider, symbolIntrospector)
    val logger = MacroUtil.mkLogger[this.type](c)

    val targetType = weakTypeOf[T]

    val UnaryWiring.AbstractSymbol(_, wireables) = reflectionProvider.symbolToWiring(SafeType(targetType))

    val (wireArgs, wireMethods) = wireables.map {
      // FIXME: FIXME COPYPASTA with below and with FactoryStrategyMacro
      case AbstractMethod(_, methodSymbol, targetKey) =>
        val tpe = targetKey.symbol.tpe
        val methodName = methodSymbol.asMethod.name.toTermName
        val argName = c.freshName(methodName)

        val anns = targetKey match {
          case idKey: DIKey.IdKey[_] =>
            import idKey._
            val ann = q"new _root_.com.github.pshirshov.izumi.distage.model.definition.Id($id)"
            Modifiers.apply(NoFlags, typeNames.EMPTY, List(ann))
          case _ =>
            Modifiers()
        }

        (q"$anns val $argName: $tpe", q"override val $methodName: $tpe = $argName")
    }.unzip

    val instantiate = if (wireMethods.isEmpty)
      q"new $targetType {}"
    else
      q"new $targetType { ..$wireMethods }"

    val constructorDef = q"""
      ${if (wireArgs.nonEmpty)
          q"def constructor(..$wireArgs): $targetType = ($instantiate).asInstanceOf[$targetType]"
        else
          q"def constructor: $targetType = ($instantiate).asInstanceOf[$targetType]"
      }
      """

    val dikeyWrappedFunction = symbolOf[DIKeyWrappedFunction.type].asClass.module
    val res = c.Expr[TraitConstructor[T]] {
      q"""
          {
          $constructorDef

          new ${weakTypeOf[TraitConstructor[T]]}($dikeyWrappedFunction.apply[$targetType](constructor _))
          }
       """
    }
    logger.log(s"Final syntax tree of trait $targetType:\n$res")

    res
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
