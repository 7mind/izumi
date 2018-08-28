package com.github.pshirshov.izumi.fundamentals.reflection

import com.github.pshirshov.izumi.fundamentals.platform.console.TrivialLogger

import scala.annotation.tailrec
import scala.language.experimental.macros
import scala.reflect.ClassTag
import scala.reflect.macros.blackbox

// TODO: benchmark difference between running implicit search inside macro vs. return tree with recursive implicit macro expansion
// TODO: benchmark ProviderMagnet vs. identity macro vs. normal function
class TagMacroImpl(val c: blackbox.Context) {
  import TagMacroImpl._
  import c.universe._

  @deprecated("")
  object TrivialMacroLogger {
    def apply[T: ClassTag](c: blackbox.Context): TrivialLogger =
      TrivialLogger.make[T]("izumi.distage.debug.macro", sink = new MacroTrivialSink(c))
  }

  protected val logger: TrivialLogger = TrivialMacroLogger[this.type](c)

  def impl[DIU <: WithTags with Singleton: c.WeakTypeTag, T: c.WeakTypeTag]: c.Expr[TagMaterializer[DIU, T]] = {

//    val universe = c.Expr[DIU](q"${c.weakTypeOf[DIU].asInstanceOf[c.universe.SingleType].sym.asTerm}")

//    c.abort(c.enclosingPosition, s"GOT UNIVERSE: ${c.weakTypeOf[DIU]}")
    logger.log(s"GOT UNIVERSE: ${c.weakTypeOf[DIU]}")

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

    val tag = mkTag[DIU, T](tgt)

    val res = reify {
      { new TagMaterializer[DIU, T](tag.splice) }
    }

    // closure allocation could be problematic
    logger.log(s"Final code of TagMaterializer[${weakTypeOf[T]}]:\n ${showRaw(res.tree, printTypes = true, printIds = true, printOwners = true, printMirrors = true)}")

    res
  }

  // we need to handle four cases – type args, refined types, type bounds and bounded wildcards(? check existence)
  @inline
  protected def mkTag[DIU <: WithTags with Singleton: c.WeakTypeTag, T: c.WeakTypeTag](tpe: c.Type): c.Expr[DIU#Tag[T]] = {

    val argHoles = tpe.typeArgs.map {
      t0 =>
        val t = norm(t0.dealias)
        t -> paramKind(t)
    }

    val ctor = tpe.dealias.typeConstructor
    val constructorTag: c.Expr[DIU#ScalaReflectTypeTag[_]] = paramKind(ctor) match {
      case None | Some(Kind(Nil)) =>
        val tpeN = holesToNothing(tpe, argHoles)
        c.Expr[DIU#ScalaReflectTypeTag[_]](q"_root_.scala.Predef.implicitly[${appliedType(weakTypeOf[DIU#ScalaReflectTypeTag[Nothing]], tpeN)}]")
//        c.Expr[DIU#ScalaReflectTypeTag[_]](q"{ null : ${appliedType(weakTypeOf[DIU#ScalaReflectTypeTag[Nothing]], tpeN)} }")
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
      case Some(hole) => summonCtor[DIU](ctor, hole)
    }

    val argTags = c.Expr[List[Option[DIU#ScalaReflectTypeTag[_]]]](q"${argHoles.map { case (t, h) => summonMergeArg[DIU](t, h) }}")

    val term = c.weakTypeOf[DIU].asInstanceOf[c.universe.SingleType].sym.asTerm
    val universe = c.Expr[DIU](q"${term}")
//    val universe = c.Expr[DIU](q"(null: ${weakTypeOf[DIU]})")

//    c.abort(c.enclosingPosition, s"GOT UNIVERSE: ${showCode(universe.tree)}")
    logger.log(s"UNIVERSE TREEE: ${showCode(universe.tree)}, fc: ${term.fullName}")

    reify {
      { universe.splice.Tag.mergeArgs[T](constructorTag.splice, argTags.splice) }
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
  protected def summonCtor[DIU <: WithTags with Singleton: c.WeakTypeTag](tpe: c.Type, kind: Kind): c.Expr[DIU#ScalaReflectTypeTag[_]] = {
    // TODO error message ???
    val summon: ImplicitSummon[c.type, c.universe.type, DIU] = kindMap[DIU].lift(kind).getOrElse(c.abort(c.enclosingPosition, "TODO"))
    val expr = summon.apply(tpe)
    expr
  }

  @inline
  protected def summonMergeArg[DIU <: WithTags with Singleton: c.WeakTypeTag](ctor: c.Type, hole: Option[Kind]): c.Expr[Option[DIU#ScalaReflectTypeTag[_]]] = hole match {
    case Some(kind) =>
      // TODO error message ???
      val summon: ImplicitSummon[c.type, c.universe.type, DIU] = kindMap[DIU].lift(kind).getOrElse(c.abort(c.enclosingPosition, "TODO"))
      reify[Option[DIU#ScalaReflectTypeTag[_]]](Some(summon.apply(ctor).splice))
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

  // TODO: performance of creation? make val
  protected def kindMap[DIU <: WithTags with Singleton: c.WeakTypeTag]: PartialFunction[Kind, ImplicitSummon[c.type, c.universe.type, DIU]] = {
    case Kind(Nil) => ImplicitSummon[c.type, c.universe.type, DIU] {
      t => c.Expr[DIU#ScalaReflectTypeTag[_]](q"_root_.scala.Predef.implicitly[${appliedType(weakTypeOf[DIU#Tag[Nothing]], t)}].tag")
    }
    case Kind(Kind(Nil) :: Nil) => ImplicitSummon[c.type, c.universe.type, DIU] {
      t => c.Expr[DIU#ScalaReflectTypeTag[_]](q"_root_.scala.Predef.implicitly[${appliedType(weakTypeOf[DIU#TagK[Nothing]], t)}].tag")
    }
    case Kind(Kind(Nil) :: Kind(Nil) :: Nil) => ImplicitSummon[c.type, c.universe.type, DIU] {
      t => c.Expr[DIU#ScalaReflectTypeTag[_]](q"_root_.scala.Predef.implicitly[${appliedType(weakTypeOf[DIU#TagKK[Nothing]], t)}].tag")
    }
  }

}

object TagMacroImpl {
  final case class Kind(args: List[Kind]) {
    override def toString: String = s"_${if (args.nonEmpty) args.mkString("[", ", ", "]") else ""}"
  }

  final case class ImplicitSummon[C <: blackbox.Context, U <: SingletonUniverse, DIU <: WithTags with Singleton](
    apply: U#Type => C#Expr[DIU#ScalaReflectTypeTag[_]]
  ) extends AnyVal
}





