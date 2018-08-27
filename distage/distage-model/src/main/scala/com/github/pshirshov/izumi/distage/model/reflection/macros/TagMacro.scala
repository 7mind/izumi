package com.github.pshirshov.izumi.distage.model.reflection.macros

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.{Tag, TagK, TagKK}
import com.github.pshirshov.izumi.fundamentals.platform.console.TrivialLogger
import com.github.pshirshov.izumi.fundamentals.reflection.SingletonUniverse

import scala.annotation.tailrec
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

    val tgt = norm(weakTypeOf[T].dealias)

    // if type head is undefined or type is undefined and has no holes: print implicit not found (steal error messages from cats-effect)

    val res = findHoles[T](tgt, foundTypeTag)

    logger.log(s"Final code of Tag[${weakTypeOf[T]}]:\n ${showCode(res.tree)}")

    res
  }

  // we need to handle four cases – type args, refined types, type bounds and bounded wildcards(? check existence)
  @inline
  protected def findHoles[T: c.WeakTypeTag](tpe: c.Type, ruTag: c.Expr[ru.WeakTypeTag[T]]): c.Expr[TagMacro[T]] = {
    val ctor = tpe.typeConstructor
    val constructorTag: c.Expr[ru.WeakTypeTag[_]] = paramKind(ctor) match {
      case None => ruTag
      case Some(hole) => summonCtor(tpe, hole)
    }
    val argTags0: List[c.Expr[Option[ru.TypeTag[_]]]] = tpe.typeArgs.map(t => summonMergeArg(t, paramKind(norm(t.dealias))))

    val argTags = c.Expr[List[Option[ru.TypeTag[_]]]](q"$argTags0")

    reify {
      TagMacro[T](Tag.mergeArgs[T](constructorTag.splice, argTags.splice))
    }
    // TODO: compounds
  }

  @inline
  protected def summonCtor(tpe: c.Type, kind: Kind): c.Expr[ru.WeakTypeTag[_]] = {
    // TODO error message ???
    val summon: ImplicitSummon[c.type, c.universe.type] = kindMap.lift(kind).getOrElse(c.abort(c.enclosingPosition, "TODO"))
    val expr = summon.apply(tpe)
    expr
  }

  @inline
  protected def summonMergeArg(tpe: c.Type, hole: Option[Kind]): c.Expr[Option[ru.TypeTag[_]]] = hole match {
    case Some(kind) =>
      // TODO error message ???
      val summon: ImplicitSummon[c.type, c.universe.type] = kindMap.lift(kind).getOrElse(c.abort(c.enclosingPosition, "TODO"))
      reify[Option[ru.TypeTag[_]]](Some(summon.apply(tpe).splice))
    case None =>
      reify(None)
  }

  @inline
  protected def paramKind(tpe: c.Type): Option[Kind] =
    if (tpe.typeSymbol.isParameter)
      Some(kindOf(tpe))
    else
      None

  protected def kindOf(tpe: c.Type): Kind =
    Kind(tpe.typeParams.map(t => kindOf(t.typeSignature)))

  /** Mini `normalize`. We don't wanna do scary things such as beta-reduce. And AFAIK the only case that can make us
    * confuse a type-parameter for a non-parameter is an empty refinement `T {}`. So we just strip it when we get it. */
  @tailrec
  final protected def norm(x: Type): Type = x match {
    case RefinedType(t :: _, m) if m.isEmpty => norm(t)
    case AnnotatedType(_, t) => norm(t)
    case _ => x
  }

  // performance of creation?
  protected val kindMap: PartialFunction[Kind, ImplicitSummon[c.type, c.universe.type]] = {
    case Kind(Nil) => ImplicitSummon[c.type, c.universe.type] {
      t => c.Expr[ru.TypeTag[_]](q"implicitly[${typeOf[Tag[Nothing]]}[$t]].tag")
    }
    case Kind(Kind(Nil) :: Nil) => ImplicitSummon[c.type, c.universe.type] {
      t => c.Expr[ru.TypeTag[_]](q"implicitly[${typeOf[TagK[Nothing]]}[$t]].tag")
    }
    case Kind(Kind(Nil) :: Kind(Nil) :: Nil) => ImplicitSummon[c.type, c.universe.type] {
      t => c.Expr[ru.TypeTag[_]](q"implicitly[${typeOf[TagKK[Nothing]]}[$t]].tag")
    }
  }

}

case class TagMacro[T](value: Tag[T])



object TagMacro extends TagMacroLowPriority {
  final case class Kind(args: List[Kind]) {
    override def toString: String = s"_${if (args.isEmpty) "" else args.mkString("[", ", ", "]")}"
  }

  final case class ImplicitSummon[C <: blackbox.Context, U <: SingletonUniverse](apply: U#Type => C#Expr[ru.TypeTag[_]]) extends AnyVal

  def get[T: TagMacro]: Tag[T] = implicitly[TagMacro[T]].value

  implicit def typetagcase[T](implicit t: ru.TypeTag[T]): TagMacro[T] = TagMacro(Tag(t))
}

trait TagMacroLowPriority {
  implicit def macrocase[T: ru.WeakTypeTag]: TagMacro[T] = macro TagMacroImpl.impl[T]
}



