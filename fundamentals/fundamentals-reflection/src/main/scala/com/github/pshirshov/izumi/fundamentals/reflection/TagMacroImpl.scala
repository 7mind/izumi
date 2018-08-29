package com.github.pshirshov.izumi.fundamentals.reflection

import com.github.pshirshov.izumi.fundamentals.platform.console.TrivialLogger

import scala.annotation.tailrec
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

  protected[this] val logger: TrivialLogger = TrivialMacroLogger[this.type](c)

  def impl[DIU <: WithTags with Singleton: c.WeakTypeTag, T: c.WeakTypeTag]: c.Expr[TagMaterializer[DIU, T]] = {

    logger.log(s"GOT UNIVERSE: ${c.weakTypeOf[DIU]}")

    logger.log(s"Got compile tag: ${weakTypeOf[T].dealias}") // have to always dealias
    // ok we don't run implicit search for concrete embedded stuff, consistent with how typetag behaves
      // FIXME: experiment with abstract types as params and their weaktypetags
    // write tests for current tag

    // unfortunately, WeakTypeTags of predefined primitives will not have inner type 'TypeTag'
    // one way around this is to match against a list of them...
    // Or put an implicit search guard first

    val tgt = norm(weakTypeOf[T].dealias)

    // if type head is undefined or type is undefined and has no holes: print implicit not found (steal error messages from cats-effect)

    val tag = tgt match {
      case RefinedType(intersection, _) =>
        c.info(c.enclosingPosition, s"TYYYPE ARGS available in $tgt: ${tgt.typeArgs.size}", true)

        mkRefined[DIU, T](intersection, tgt)
      case _ if tgt.typeArgs.isEmpty =>
        c.abort(c.enclosingPosition, "TODO")

      // FIXME: obviously if ctor is kind 0 and is found to be a parameter we should fail
      // alternatively, we could drop the entire parameter check shit and just summon typetags
      // this would generally be far more correct, robust and simple
      // i.e. get benchmarks first before you commit to a more brittle solution
      // Objection: it's not necessarily more valid, parameters may hide which do not have valid typetags, e.g X[Id, ?]
      case _ =>
        mkTag[DIU, T](tgt)
    }

    val res = reify {
      { new TagMaterializer[DIU, T](tag.splice) }
    }

    // log closure allocation could be problematic
    logger.log(s"Final code of TagMaterializer[${weakTypeOf[T]}]:\n ${showCode(res.tree)}")

    res
  }

  @inline
  protected[this] def mkRefined[DIU <: WithTags with Singleton: c.WeakTypeTag, T: c.WeakTypeTag](intersection: List[Type], struct: Type): c.Expr[DIU#Tag[T]] = {
    val intersectionsTags = c.Expr[List[DIU#ScalaReflectTypeTag[_]]](q"${
      intersection.map {
        t0 =>
          val t = norm(t0.dealias)
          summonTag[DIU](t)
      }
    }")
    val structTag = mkStruct[DIU](c.internal.refinedType(intersection, c.internal.newScopeWith()), struct)

    val universe = universeSingleton[DIU]

    logger.log(s"UNIVERSE TREEE: ${showCode(universe.tree)}")

    reify {
      { universe.splice.Tag.refinedTag[T](intersectionsTags.splice, structTag.splice) }
    }
  }

  @inline
  // have to tag along the original intersection, because scalac dies on trying to summon typetag for custom `internal.refinedType` ...
  protected[this] def mkStruct[DIU <: WithTags with Singleton: c.WeakTypeTag](i: c.Type, struct: Type): c.Expr[DIU#ScalaReflectWeakTypeTag[_]] = {
    // replace param members with Nothing, replace types by TermName("string")
    summonWeakTypeTag[DIU](i, struct)
  }

  // we need to handle four cases – type args, refined types, type bounds and bounded wildcards(? check existence)
  @inline
  protected[this] def mkTag[DIU <: WithTags with Singleton: c.WeakTypeTag, T: c.WeakTypeTag](tpe: c.Type): c.Expr[DIU#Tag[T]] = {

    val argHoles = tpe.typeArgs.map {
      t0 =>
        val t = norm(t0.dealias)
        t -> paramKind(t)
    }

    val ctor = tpe.dealias.typeConstructor
    val constructorTag: c.Expr[DIU#ScalaReflectTypeTag[_]] = paramKind(ctor) match {
      case None =>
        val tpeN = holesToNothing(tpe.dealias, argHoles)
        c.info(c.enclosingPosition, s"SDKLFJSDKLJJFSLDKFJ $tpeN", true)
        summonTypeTag[DIU](tpeN)

      case Some(Kind(Nil)) =>
        // TODO ???
        c.abort(c.enclosingPosition, "TODO")
        // can't determine abstract types... they may be valid non-parameter but without tags...
      case Some(hole) =>
        summon[DIU](ctor, hole)
    }

    val argTags = c.Expr[List[Option[DIU#ScalaReflectTypeTag[_]]]](q"${argHoles.map { case (t, h) => summonMergeArg[DIU](t, h) }}")

    val universe = universeSingleton[DIU]

    logger.log(s"UNIVERSE TREEE: ${showCode(universe.tree)}")

    reify {
      { universe.splice.Tag.appliedTag[T](constructorTag.splice, argTags.splice.map(_.get)) }
    }
    // TODO: compounds
  }

  @inline
  protected[this] def summonTypeTag[DIU <: WithTags with Singleton: c.WeakTypeTag](tpeN: c.Type): c.Expr[DIU#ScalaReflectTypeTag[_]] =
    c.Expr[DIU#ScalaReflectTypeTag[_]](q"_root_.scala.Predef.implicitly[${appliedType(weakTypeOf[DIU#ScalaReflectTypeTag[Nothing]], tpeN)}]")

  @inline
  protected[this] def summonWeakTypeTag[DIU <: WithTags with Singleton: c.WeakTypeTag](i: c.Type, tpeN: c.Type): c.Expr[DIU#ScalaReflectWeakTypeTag[_]] =
    c.Expr[DIU#ScalaReflectWeakTypeTag[_]](q"_root_.scala.Predef.implicitly[${appliedType(weakTypeOf[DIU#ScalaReflectWeakTypeTag[Nothing]], tpeN)}]")

  @inline
  protected[this] def holesToNothing(tpe: c.Type, args: List[(c.Type, Option[Kind])]): c.Type = {
    val newArgs = args.map {
      case (t, None) => t
      case _ => definitions.NothingTpe
    }
    // TODO handle refinements differently
    tpe match {
      case RefinedType(t :: ts, r) =>
        val nt = c.internal.refinedType(c.universe.appliedType(t, newArgs) :: ts, r)
        c.info(c.enclosingPosition, s"IS REFINED N: $nt dfogdfg $tpe ; args: $newArgs", true)
        assert(nt.isInstanceOf[RefinedType])
      case _ =>
        c.info(c.enclosingPosition, s"IS NOT REFINED !!! $tpe class ${tpe.getClass}: parents ${tpe.getClass.getClasses}", true)
    }
    c.universe.appliedType(tpe, newArgs)
  }

  @inline
  protected[this] def summonTag[DIU <: WithTags with Singleton: c.WeakTypeTag](tpe: c.Type): c.Expr[DIU#ScalaReflectTypeTag[_]] =
    summon[DIU](tpe, kindOf(tpe))

  @inline
  protected[this] def summon[DIU <: WithTags with Singleton: c.WeakTypeTag](tpe: c.Type, kind: Kind): c.Expr[DIU#ScalaReflectTypeTag[_]] = {
    // TODO error message on no kind ???
    val summon: ImplicitSummon[c.type, c.universe.type, DIU] = kindMap[DIU].lift(kind).getOrElse(c.abort(c.enclosingPosition, "TODO"))
    summon.apply(tpe)
  }

  @inline
  protected[this] def summonMergeArg[DIU <: WithTags with Singleton: c.WeakTypeTag](ctor: c.Type, hole: Option[Kind]): c.Expr[Option[DIU#ScalaReflectTypeTag[_]]] =
//    reify(Some(summonTag[DIU](ctor).splice))
    hole match {
    case Some(kind) =>
      // TODO error message on no kind ???
      reify(Some(summon[DIU](ctor, kind).splice))
    case None =>
//      reify(None)
      // FIXME WTF
      reify(Some(summon[DIU](ctor, kindOf(ctor)).splice))
  }

  @inline
  protected[this] def paramKind(tpe: c.Type): Option[Kind] =
  // c.internal.isFreeType ?
    if (tpe.typeSymbol.isParameter)
      Some(kindOf(tpe))
    else
      None

  protected[this] def kindOf(tpe: c.Type): Kind =
    Kind(tpe.typeParams.map(t => kindOf(t.typeSignature)))

  /** Mini `normalize`. We don't wanna do scary things such as beta-reduce. And AFAIK the only case that can make us
    * confuse a type-parameter for a non-parameter is an empty refinement `T {}`. So we just strip it when we get it. */
  @tailrec
  protected[this] final def norm(x: Type): Type = x match {
    case RefinedType(t :: Nil, m) if m.isEmpty =>
      c.info(c.enclosingPosition, s"Stripped empty refinement of type $t. member scope $m, true)", true)
      norm(t)
    case AnnotatedType(_, t) => norm(t)
    case _ => x
  }

  @inline
  protected[this] def universeSingleton[DIU: c.WeakTypeTag]: c.Expr[DIU] = {
    val term = c.weakTypeOf[DIU] match {
          case u: SingleType => u.sym.asTerm
          case u => c.abort(c.enclosingPosition,
            s"""Got a non-singleton universe type - $u. Please instantiate universe as
               | a val or an object and keep it somewhere in scope!!""".stripMargin)
        }
    c.Expr[DIU](q"$term")
  }

  // TODO: performance of creation? make val
  protected[this] def kindMap[DIU <: WithTags with Singleton: c.WeakTypeTag]: PartialFunction[Kind, ImplicitSummon[c.type, c.universe.type, DIU]] = {
    case Kind(Nil) => ImplicitSummon[c.type, c.universe.type, DIU] {
      t =>
        // workaround for false implicit divergence after expansion
        val name = TermName(c.freshName())
        c.Expr[DIU#ScalaReflectTypeTag[_]](q"""
           { def $name(implicit ev: ${appliedType(weakTypeOf[DIU#Tag[Nothing]], t)}) = ev; $name.tag }""")
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

  final case class ImplicitSummon[C <: blackbox.Context with Singleton, U <: SingletonUniverse, DIU <: WithTags with Singleton](
    apply: U#Type => C#Expr[DIU#ScalaReflectTypeTag[_]]
  ) extends AnyVal
}





