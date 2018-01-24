package org.bitbucket.pshirshov.izumi.distage.provisioning.traitcompiler

import org.bitbucket.pshirshov.izumi.distage.definition.WrappedFunction
import org.bitbucket.pshirshov.izumi.distage.model.EqualitySafeType
import org.bitbucket.pshirshov.izumi.distage.reflection.{SymbolIntrospector, SymbolIntrospectorDefaultImpl}
import org.bitbucket.pshirshov.izumi.distage.{TypeNative, TypeSymb}

import scala.language.experimental.macros
import scala.reflect.macros.whitebox

// TODO: use scala-asm to generate traits at runtime instead.

object TraitConstructorMacro extends TraitConstructorMacro {
  final val symbolIntrospector = SymbolIntrospectorDefaultImpl.instance

  def mkWrappedTraitConstructor[T]: WrappedFunction[T] = macro wrappedTestImpl[T]

  def mkTraitConstructor[T]: Any = macro testImpl[T]
}

trait TraitConstructorMacro {

  def symbolIntrospector: SymbolIntrospector

  def testImpl[T: c.WeakTypeTag](c: whitebox.Context): c.Expr[Any] = {
    import c.universe._

    val className = tq"${weakTypeTag[T]}"

//    System.err.println(s"GOT CLASSNAME $className")

    val targetType = weakTypeOf[T]
    weakTypeTag[T].tpe.decls

    //`declaration` takes only current; `members` also takes inherited
    val (args, m) = targetType
      .members
      .sorted
      .collect(makeDeclsAndConstructorArgs(c)(targetType))
      .unzip

//    System.err.println(s"GOT ARGSS AND METHODS $args , $m")

    if (args.isEmpty) {
      c.Expr {
        q"{ (() => (new $className {}).asInstanceOf[$className]) }"
      }
    } else {
      c.Expr {
        q"""
      {

      def x(..$args): $className = (new $className {
      ..$m
      }).asInstanceOf[$className]

      (x _)
      }
      """
      }
    }
  }

  private def makeDeclsAndConstructorArgs(c: whitebox.Context)(targetType: c.universe.Type)
    : c.universe.Symbol PartialFunction (c.universe.Tree, c.universe.Tree) = {
    import c.universe._
    {
      case d if symbolIntrospector.isWireableMethod(
          EqualitySafeType(targetType.asInstanceOf[TypeNative])
          , d.asInstanceOf[TypeSymb]
      ) =>

        val resType = d.typeSignature.resultType
        val argName = TermName(c.freshName())

        val ctorArg = q"""$argName: $resType"""
        val method = q"""override def ${d.asMethod.name}: $resType = $argName"""
        (ctorArg, method)
    }
  }

  def wrappedTestImpl[T: c.WeakTypeTag](c: whitebox.Context): c.Expr[WrappedFunction[T]] = {
    import c.universe._

    val expr = testImpl[T](c)

    val wrappedFunction = typeOf[WrappedFunction[_]].typeSymbol

    c.Expr[WrappedFunction[T]] {
      q"""
          {
          val ctor = ${reify(expr.splice)}

          identity[$wrappedFunction[${weakTypeTag[T]}]](ctor)
          }
       """
    }
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
