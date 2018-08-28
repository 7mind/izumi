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

  def impl[T: c.WeakTypeTag]: c.Tree = { //: c.Expr[Tag[T]] = {
    logger.log(s"Got compile tag: ${weakTypeOf[T].dealias}") // have to always dealias
    // ok we don't run implicit search for concrete embedded stuff, consistent with how typetag behaves
      // FIXME: experiment with abstract types as params and their weaktypetags
    // write tests for current tag

      // unfortunately, WeakTypeTags of predefined primitives will not have inner type 'TypeTag'
      // one way around this is to match against a list of them...
      // Or put an implicit search guard first

//    logger.log(s"Got runtime tag: ${showCode(foundTypeTag.tree)}")
//    if (foundTypeTag.actualType =:= weakTypeOf[ru.TypeTag[T]] || innerActualType(foundTypeTag).fold(false)(_ =:= weakTypeOf[ru.TypeTag[T]])) {
//      logger.log("AAAAAH! got full tag!!!")
//    }

    val tgt = norm(weakTypeOf[T].dealias)

    // if type head is undefined or type is undefined and has no holes: print implicit not found (steal error messages from cats-effect)

    val res = findHoles[T](tgt)

    // closure allocation could be problematic
    logger.log(s"Final code of Tag[${weakTypeOf[T]}]:\n ${showCode(res.tree)}")

    res

    res.tree
  }

  // we need to handle four cases – type args, refined types, type bounds and bounded wildcards(? check existence)
  @inline
  protected def findHoles[T: c.WeakTypeTag](tpe: c.Type): c.Expr[Tag[T]] = {

    val argHoles = tpe.typeArgs.map {
      t0 =>
        val t = norm(t0.dealias)
        t -> paramKind(t)
    }

    val ctor = tpe.dealias.typeConstructor
    val constructorTag: c.Expr[ru.WeakTypeTag[_]] = paramKind(ctor) match {
      case None | Some(Kind(Nil)) =>
        val tpeN = holesToNothing(tpe, argHoles)
        c.Expr[ru.TypeTag[_]](q"_root_.scala.Predef.implicitly[${appliedType(weakTypeOf[ru.TypeTag[Nothing]], tpeN)}]")
//
//      case Some(Kind(Nil)) =>
//        // TODO ???
//        c.abort(c.enclosingPosition, "TODO")
        // can't determine abstract types... they may be valid non-parameter but without tags...

        // FIXME: obviously if ctor is kind 0 and is found to be a parameter we should fail
        // alternatively, we could drop the entire parameter check shit and just summon typetags
        // this would generally be far more correct, robust and simple
        // i.e. get benchmarks first before you commit to a more brittle solution
        // Objection: it's not necessarily more valid, parameters may hide which do not have valid typetags, e.g X[Id, ?]
      case Some(hole) => summonCtor(ctor, hole)
    }

    val argTags = c.Expr[List[Option[ru.TypeTag[_]]]](q"${argHoles.map { case (t, h) => summonMergeArg(t, h) }}")

    reify {
      Tag.mergeArgs[T](constructorTag.splice, argTags.splice)
    }
    // TODO: compounds
  }

  @inline
  protected def holesToNothing(tpe: c.Type, args: List[(c.Type, Option[Kind])]): c.Type = {
    val newArgs = args.map {
      case (t, None) => t
      case _ => definitions.NothingTpe
    }
    appliedType(tpe.typeConstructor, newArgs)
  }

  @inline
  protected def summonCtor(tpe: c.Type, kind: Kind): c.Expr[ru.WeakTypeTag[_]] = {
    // TODO error message ???
    val summon: ImplicitSummon[c.type, c.universe.type] = kindMap.lift(kind).getOrElse(c.abort(c.enclosingPosition, "TODO"))
    val expr = summon.apply(tpe)
    expr
  }

  @inline
  protected def summonMergeArg(ctor: c.Type, hole: Option[Kind]): c.Expr[Option[ru.TypeTag[_]]] = hole match {
    case Some(kind) =>
      // TODO error message ???
      val summon: ImplicitSummon[c.type, c.universe.type] = kindMap.lift(kind).getOrElse(c.abort(c.enclosingPosition, "TODO"))
      reify[Option[ru.TypeTag[_]]](Some(summon.apply(ctor).splice))
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
      t => c.Expr[ru.TypeTag[_]](q"_root_.scala.Predef.implicitly[${appliedType(weakTypeOf[Tag[Nothing]], t)}].tag")
    }
    case Kind(Kind(Nil) :: Nil) => ImplicitSummon[c.type, c.universe.type] {
      t => c.Expr[ru.TypeTag[_]](q"_root_.scala.Predef.implicitly[${appliedType(weakTypeOf[TagK[Nothing]], t)}].tag")
    }
    case Kind(Kind(Nil) :: Kind(Nil) :: Nil) => ImplicitSummon[c.type, c.universe.type] {
      t => c.Expr[ru.TypeTag[_]](q"_root_.scala.Predef.implicitly[${appliedType(weakTypeOf[TagKK[Nothing]], t)}].tag")
    }
  }

}


object TagMacro {
  final case class Kind(args: List[Kind]) {
    override def toString: String = s"_${if (args.isEmpty) "" else args.mkString("[", ", ", "]")}"
  }

  final case class ImplicitSummon[C <: blackbox.Context, U <: SingletonUniverse](apply: U#Type => C#Expr[ru.TypeTag[_]]) extends AnyVal

  def get[T: Tag]: Tag[T] = implicitly[Tag[T]]
}
