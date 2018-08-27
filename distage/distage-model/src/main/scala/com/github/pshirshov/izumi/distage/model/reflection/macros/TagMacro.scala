package com.github.pshirshov.izumi.distage.model.reflection.macros

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.{Tag, TagK, TagKK}
import com.github.pshirshov.izumi.fundamentals.platform.console.TrivialLogger
import com.github.pshirshov.izumi.fundamentals.reflection.SingletonUniverse

import scala.annotation.tailrec
import scala.collection.immutable.Nil
import scala.language.experimental.macros
import scala.reflect.macros.blackbox
import scala.reflect.runtime.{universe => ru}

// TODO: benchmark difference between running implicit search inside macro vs. return tree with recursive implicit macro expansion
// TODO: benchmark ProviderMagnet vs. identity macro vs. normal function
class TagMacroImpl(val c: blackbox.Context) {
  import c.universe._
  import TagMacro._

  protected val logger: TrivialLogger = TrivialMacroLogger[this.type](c)

  private[this] def innerActualType(e: c.Expr[_]): Option[c.Type] =
    e.tree match {
      case Typed(t, _) =>
        Option(t.tpe)
      case _ =>
        None
    }

  def impl[T: c.WeakTypeTag](foundTypeTag: c.Expr[ru.WeakTypeTag[T]]): c.Expr[TagMacro[T]] = {
    logger.log(s"Got compile tag: ${weakTypeOf[T].typeArgs}")
    logger.log(s"Got compile tag: ${weakTypeOf[T].dealias.typeArgs}") // have to always dealias
    logger.log(s"Got runtime tag: ${showCode(foundTypeTag.tree)}")

    if (foundTypeTag.actualType =:= weakTypeOf[ru.TypeTag[T]] || innerActualType(foundTypeTag).fold(false)(_ =:= weakTypeOf[ru.TypeTag[T]])) {
      // unfortunately, WeakTypeTags of predefined primitives will not have inner type 'TypeTag'
      // one way around this is to match against a list of them...
      // Or put an implicit search guard first
      logger.log("AAAAAH! got full tag!!!")
    }

    val tgt = weakTypeOf[T].dealias

    // if type head is undefined or type is undefined and has no holes: print implicit not found (steal error messages from cats-effect)

    val res = reify[TagMacro[T]] {
      TagMacro[T](Tag[T](foundTypeTag.splice
        .asInstanceOf[ru.TypeTag[T]]
      ))
    }

    logger.log(s"Final code of Tag[${weakTypeOf[T]}]:\n ${showCode(res.tree)}")

    res
  }

  // we need to handle four cases – type args, refined types, type bounds and bounded wildcards(? check existence)
  @inline
  protected def findHoles[T: c.WeakTypeTag](tpe: c.Type): Any = { //List[c.Type] => c.Expr[TagMacro[T]] = {
    val constructorHole = paramKind(tpe.typeConstructor)
    val argHoles = tpe.typeArgs.map(t => paramKind(norm(t.dealias)))

//    val ctor = defaiult(constructorHole)
//    reify {
//      Tag.appliedTag[T](constructorHole.splice, argHoles.splice)
//    }
    // TODO: compounds
  }

  @inline
  def paramKind(tpe: c.Type): Option[Kind] =
    if (tpe.typeSymbol.isParameter)
      Some(kindOf(tpe))
    else
      None

  def kindOf(tpe: c.Type): Kind =
    Kind(tpe.typeParams.map(t => kindOf(t.typeSignature)))

  /** Mini `normalize`. We don't wanna do scary things such as beta-reduce. And AFAIK the only case that can make us
    * confuse a type-parameter for a non-parameter is an empty refinement `T {}`. So we just strip it when we get it. */
  @inline
  def norm(x: Type): Type = x match {
    case RefinedType(t :: _, m) if m.isEmpty => t
    case _ => x
  }

  protected val kindMap: PartialFunction[Kind, ImplicitSummon[c.universe.type]] = {
    case Kind(Nil) => ImplicitSummon {
      t => q"implicitly[${typeOf[Tag[Nothing]]}[$t]].tag"
    }
    case Kind(Kind(Nil) :: Nil) => ImplicitSummon {
      t => q"implicitly[${typeOf[TagK[Nothing]]}[$t]].tag"
    }
    case Kind(Kind(Nil) :: Kind(Nil) :: Nil) => ImplicitSummon {
      t => q"implicitly[${typeOf[TagKK[Nothing]]}[$t]].tag"
    }
  }

}

case class TagMacro[T](value: Tag[T])



object TagMacro extends TagMacroLowPriority {
  final case class Kind(args: List[Kind]) {
    override def toString: String = s"_${if (args.isEmpty) "" else args.mkString("[", ", ", "]")}"
  }

  final case class ImplicitSummon[U <: SingletonUniverse](f: U#Type => U#Tree) extends AnyVal

  def get[T: TagMacro]: Tag[T] = implicitly[TagMacro[T]].value

  implicit def typetagcase[T](implicit t: ru.TypeTag[T]): TagMacro[T] = TagMacro(Tag(t))
}

trait TagMacroLowPriority {
  implicit def macrocase[T: ru.WeakTypeTag]: TagMacro[T] = macro TagMacroImpl.impl[T]
}



