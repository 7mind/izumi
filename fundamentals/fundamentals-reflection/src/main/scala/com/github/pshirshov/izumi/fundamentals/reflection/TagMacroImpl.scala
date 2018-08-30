package com.github.pshirshov.izumi.fundamentals.reflection

import com.github.pshirshov.izumi.fundamentals.platform.console.TrivialLogger
import WithTags.{defaultTagImplicitError, hktagFormat, hktagFormatMap}
import ReflectionUtil.{Kind, kindOf}

import scala.annotation.{implicitNotFound, tailrec}
import scala.collection.immutable.ListMap
import scala.reflect.ClassTag
import scala.reflect.macros.{TypecheckException, blackbox}

// TODO: benchmark difference between running implicit search inside macro vs. return tree with recursive implicit macro expansion
// TODO: benchmark difference between searching all arguments vs. merge strategy
// TODO: benchmark ProviderMagnet vs. identity macro vs. normal function
class TagMacroImpl(val c: blackbox.Context) {
  import TagMacroImpl._
  import c.universe._

  private[this] final var nested: Boolean = false

  protected[this] val defaultError: String = defaultTagImplicitError

  @deprecated("")
  object TrivialMacroLogger {
    def apply[T: ClassTag](c: blackbox.Context): TrivialLogger =
      TrivialLogger.make[T]("izumi.distage.debug.macro", sink = new MacroTrivialSink(c))
  }

  protected[this] val logger: TrivialLogger = TrivialMacroLogger[this.type](c)

  def impl[DIU <: WithTags with Singleton: c.WeakTypeTag, T: c.WeakTypeTag]: c.Expr[TagMaterializer[DIU, T]] = {

    logger.log(s"GOT UNIVERSE: ${c.weakTypeOf[DIU]}")
    logger.log(s"Got compile tag: ${weakTypeOf[T].dealias}")

    if (getImplicitError[DIU]().endsWith(":")) // I know
      nested = true
    else
      resetImplicitError[DIU]()

    val tgt = norm(weakTypeOf[T].dealias)

    val universe = universeSingleton[DIU]
    logger.log(s"Universe object: ${showCode(universe.tree)}")

    addImplicitError(s"Deriving Tag for $tgt:")

    val tag = tgt match {
      case RefinedType(intersection, _) =>
        mkRefined[DIU, T](universe, intersection, tgt)
      case _ =>
        mkTag[DIU, T](universe, tgt)
    }

    val res = reify {
      { new TagMaterializer[DIU, T](tag.splice) }
    }

    logger.log(s"Final code of TagMaterializer[${weakTypeOf[T]}]:\n ${showCode(res.tree)}")

    res
  }

  @inline
  protected[this] def mkRefined[DIU <: WithTags with Singleton: c.WeakTypeTag, T: c.WeakTypeTag](universe: c.Expr[DIU], intersection: List[Type], struct: Type): c.Expr[DIU#Tag[T]] = {

    val intersectionsTags = c.Expr[List[DIU#ScalaReflectTypeTag[_]]](q"${
      intersection.map {
        t0 =>
          val t = norm(t0.dealias)
          summonTag[DIU](t)
      }
    }")
    val structTag = mkStruct[DIU](struct)

    reify {
      { universe.splice.Tag.refinedTag[T](intersectionsTags.splice, structTag.splice) }
    }
  }

  @inline
  // have to tag along the original intersection, because scalac dies on trying to summon typetag for a custom made refinedType from `internal.refinedType` ...
  protected[this] def mkStruct[DIU <: WithTags with Singleton: c.WeakTypeTag](struct: Type): c.Expr[DIU#ScalaReflectWeakTypeTag[_]] = {

    struct.decls.find(_.info.typeSymbol.isParameter).foreach {
      s =>
        val msg = s"Encountered a type parameter ${s.info} as a part of structural refinement of $struct: It's not yet supported to summon a Tag for ${s.info} in that position!"

        addImplicitError(msg)
        c.abort(c.enclosingPosition, msg)
    }

    // TODO: replace types of members with Nothing, in runtime replace types to types from tags searching by TermName("")
    summonWeakTypeTag[DIU](struct)
  }

  // we need to handle four cases – type args, refined types, type bounds and bounded wildcards(? check existence)
  @inline
  protected[this] def mkTag[DIU <: WithTags with Singleton: c.WeakTypeTag, T: c.WeakTypeTag](universe: c.Expr[DIU], tpe: c.Type): c.Expr[DIU#Tag[T]] = {

    val argHoles = tpe.typeArgs.map {
      t0 =>
        val t = norm(t0.dealias)
        t -> paramKind(t)
    }
    val ctor = tpe.typeConstructor

    val constructorTag: c.Expr[DIU#ScalaReflectTypeTag[_]] = paramKind(ctor) match {
      case None =>
        val tpeN = holesToNothing(tpe.dealias, argHoles)
        logger.log(s"Type after replacing with Nothing $tpeN, replaced args $argHoles")
        summonTypeTag[DIU](tpeN)
      case Some(Kind(Nil)) =>
        c.abort(c.enclosingPosition, s"Encountered type parameter $tpe without TypeTag or Tag, user error: aborting")
      case Some(hole) =>
        summon[DIU](ctor, hole)
    }
    val argTags = c.Expr[List[Option[DIU#ScalaReflectTypeTag[_]]]](q"${argHoles.map { case (t, h) => summonMergeArg[DIU](t, h) }}")

    reify {
      { universe.splice.Tag.appliedTag[T](constructorTag.splice, argTags.splice.map(_.get)) }
    }
  }

  @inline
  protected[this] def summonTypeTag[DIU <: WithTags with Singleton: c.WeakTypeTag](tpeN: c.Type): c.Expr[DIU#ScalaReflectTypeTag[_]] =
    c.Expr[DIU#ScalaReflectTypeTag[_]](q"_root_.scala.Predef.implicitly[${appliedType(weakTypeOf[DIU#ScalaReflectTypeTag[Nothing]], tpeN)}]")

  @inline
  protected[this] def summonWeakTypeTag[DIU <: WithTags with Singleton: c.WeakTypeTag](tpeN: c.Type): c.Expr[DIU#ScalaReflectWeakTypeTag[_]] =
    c.Expr[DIU#ScalaReflectWeakTypeTag[_]](q"_root_.scala.Predef.implicitly[${appliedType(weakTypeOf[DIU#ScalaReflectWeakTypeTag[Nothing]], tpeN)}]")

  @inline
  // TODO: remove args parameter, inline everything back.
  protected[this] def holesToNothing(tpe: c.Type, args: List[(c.Type, Option[Kind])]): c.Type = {
    val newArgs = args.map {
//      case (t, None) => t // fucking merge arg artefact
      case _ => definitions.NothingTpe
    }
    c.universe.appliedType(tpe, newArgs)
  }

  @inline
  protected[this] def summonTag[DIU <: WithTags with Singleton: c.WeakTypeTag](tpe: c.Type): c.Expr[DIU#ScalaReflectTypeTag[_]] =
    summon[DIU](tpe, kindOf(tpe))

  @inline
  protected[this] def summon[DIU <: WithTags with Singleton: c.WeakTypeTag](tpe: c.Type, kind: Kind): c.Expr[DIU#ScalaReflectTypeTag[_]] = {

    val nothingfulTpe = c.universe.appliedType(tpe, kind.args.map(_ =>

      definitions.IntTpe

    ))

    val summoned = try {
      if (kind == Kind(Nil)) {
        c.inferImplicitValue(appliedType(weakTypeOf[DIU#Tag[Nothing]].typeConstructor, tpe), silent = false)
      } else if (kind == Kind(Kind(Nil) :: Nil)) {
        c.inferImplicitValue(appliedType(weakTypeOf[DIU#TagK[Nothing]].typeConstructor, tpe), silent = false)
      } else if (kind == Kind(Kind(Nil) :: Kind(Nil) :: Nil)) {
        c.inferImplicitValue(appliedType(weakTypeOf[DIU#TagKK[Nothing]].typeConstructor, tpe), silent = false)
      } else {

        val argsSizeType = {
          val mutType = typeOf[{type x}]
          internal.setName(mutType.decls.head, TypeName(s"${kind.args.size}"))
          mutType
        }
        logger.log(s"Created args size handle: $argsSizeType")

//        val t = TypeName(s"${kind.args.size}")
//        val lambda = c.typecheck(tq"({ type l[K[${TypeName("_")}]] = ${weakTypeOf[DIU#HKTag[Nothing, Nothing]].typeConstructor}[{type $t} , K[${definitions.NothingTpe}]] })")
//        logger.log(s"lambda tpe  tree ${showRaw(lambda)}\ncode ${showCode(lambda)}")
        c.inferImplicitValue(appliedType(weakTypeOf[DIU#HKTag[Nothing, Nothing]].typeConstructor, argsSizeType, nothingfulTpe), silent = false)
      }
    } catch {
      case e: TypecheckException =>
        setImplicitError(
          s"""could not find implicit value for ${hktagFormat(tpe)}.\n
             |${hktagFormatMap.get(kind) match {
            case Some(t) => s"Please ensure that $tpe has a $t implicit available in scope."
            case None =>
              s"""$tpe is of a kind $kind, which doesn't have a tag name. Please create a tag synonym as follows:\n\n
                 |  type TagXXX[${kind.format("K")}] = HKTag[{ type `${kind.args.size}` }, K[${List.fill(kind.args.size)("Nothing").mkString(", ")}]]\n\n
                 |And use it in your context bound, as in def x[$tpe: TagXXX] = ...
               """.stripMargin
          }
          }
           """.stripMargin)
        throw e
    }

    c.Expr[DIU#ScalaReflectTypeTag[_]](q"{$summoned.tag}")
  }

  @inline
  protected[this] def summonMergeArg[DIU <: WithTags with Singleton: c.WeakTypeTag](ctor: c.Type, hole: Option[Kind]): c.Expr[Option[DIU#ScalaReflectTypeTag[_]]] =
    // FIXME: merge
    reify(Some(summon[DIU](ctor, kindOf(ctor)).splice))

  @inline
  protected[this] def paramKind(tpe: c.Type): Option[Kind] =
  // c.internal.isFreeType ?
    if (tpe.typeSymbol.isParameter)
      Some(kindOf(tpe))
    else
      None

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
    val value = c.weakTypeOf[DIU] match {
          case u: SingleType =>
            u.sym.asTerm
          case u => c.abort(c.enclosingPosition,
            s"""Got a non-singleton universe type - $u. Please instantiate universe as
               | a val or an object and keep it somewhere in scope!!""".stripMargin)
        }
    c.Expr[DIU](q"$value")
  }

  @inline
  protected[this] def getImplicitError[DIU <: WithTags with Singleton: c.WeakTypeTag](): String =
    symbolOf[DIU#Tag[Any]].annotations.headOption.flatMap(
      AnnotationTools.findArgument(_) {
        case Literal(Constant(s: String)) => s
      }
    ).getOrElse(defaultError)

  @inline
  protected[this] def addImplicitError[DIU <: WithTags with Singleton: c.WeakTypeTag](err: String): Unit =
    setImplicitError(s"${getImplicitError()}\n$err")

  @inline
  protected[this] def setImplicitError[DIU <: WithTags with Singleton: c.WeakTypeTag](err: String): Unit = {
    import internal.decorators._

    symbolOf[DIU#Tag[Any]].setAnnotations(Annotation(typeOf[implicitNotFound], List[Tree](Literal(Constant(err))), ListMap.empty))
  }

  @inline
  protected[this] def resetImplicitError[DIU <: WithTags with Singleton: c.WeakTypeTag](): Unit =
    setImplicitError[DIU](defaultError)

  // TODO: performance of creation? make val
//  protected[this] def kindMap[DIU <: WithTags with Singleton: c.WeakTypeTag]: PartialFunction[Kind, ImplicitSummon[c.type, c.universe.type, DIU]] = {
//    case Kind(Nil) => ImplicitSummon[c.type, c.universe.type, DIU] {
//      t =>
//        // workaround for false implicit divergence after expansion
//        val name = TermName(c.freshName())
//        val param = TermName(c.freshName())
//        val implicitMsg = s"couldn't find Tag for type parameter $t"
//          try {
//            c.inferImplicitValue(appliedType(weakTypeOf[DIU#Tag[Nothing]], t), silent = false)
//          } catch {
//            case (e: Throwable) =>
//              setImplicitError(s"NO TAAG $e")
//              throw e
//          }
//        // TODO: in 2.13 we can use these little functions to enrich error messages further (possibly remove .setAnnotation hack completely) by attaching implicitNotFound to parameter
//        c.Expr[DIU#ScalaReflectTypeTag[_]](q"""
//           { def $name(implicit @_root_.scala.annotation.implicitNotFound($implicitMsg)
//          $param: ${appliedType(weakTypeOf[DIU#Tag[Nothing]], t)}) = $param; $name.tag }""")
//    }
//    case Kind(Kind(Nil) :: Nil) => ImplicitSummon[c.type, c.universe.type, DIU] {
//      t =>
//          try {
//            c.inferImplicitValue(appliedType(weakTypeOf[DIU#TagK[Nothing]], t), silent = false)
//          } catch {
//            case (e: Throwable) =>
//              setImplicitError(s"NO TAAG $e")
//              throw e
//          }
//        c.Expr[DIU#ScalaReflectTypeTag[_]](q"_root_.scala.Predef.implicitly[${appliedType(weakTypeOf[DIU#TagK[Nothing]], t)}].tag")
//    }
//    case Kind(Kind(Nil) :: Kind(Nil) :: Nil) => ImplicitSummon[c.type, c.universe.type, DIU] {
//      t =>
//          try {
//            c.inferImplicitValue(appliedType(weakTypeOf[DIU#TagKK[Nothing]], t), silent = false)
//          } catch {
//            case (e: Throwable) =>
//              setImplicitError(s"NO TAAG $e")
//              throw e
//          }
//        c.Expr[DIU#ScalaReflectTypeTag[_]](q"_root_.scala.Predef.implicitly[${appliedType(weakTypeOf[DIU#TagKK[Nothing]], t)}].tag")
//    }
//  }


  protected[this] def kindMap[DIU <: WithTags with Singleton: c.WeakTypeTag]: PartialFunction[Kind, ImplicitSummon[c.type, c.universe.type, DIU]] = {
    case Kind(Nil) => ImplicitSummon[c.type, c.universe.type, DIU] {
      t => c.Expr[DIU#ScalaReflectTypeTag[_]](q"{${          try {
                  c.inferImplicitValue(appliedType(weakTypeOf[DIU#Tag[Nothing]].typeConstructor, t), silent = false)
                } catch {
                  case (e: Throwable) =>
                    setImplicitError(s"NO TAG for $t")
                    throw e
                }}}.tag")
    }
    case Kind(Kind(Nil) :: Nil) => ImplicitSummon[c.type, c.universe.type, DIU] {
      t => c.Expr[DIU#ScalaReflectTypeTag[_]](q"{${          try {
                  c.inferImplicitValue(appliedType(weakTypeOf[DIU#TagK[Nothing]].typeConstructor, t), silent = false)
                } catch {
                  case (e: Throwable) =>
                    setImplicitError(s"NO TAGK for $t")
                    throw e
                }}}.tag")
    }
    case Kind(Kind(Nil) :: Kind(Nil) :: Nil) => ImplicitSummon[c.type, c.universe.type, DIU] {
      t => c.Expr[DIU#ScalaReflectTypeTag[_]](q"{${          try {
                  c.inferImplicitValue(appliedType(weakTypeOf[DIU#TagKK[Nothing]].typeConstructor, t), silent = false)
                } catch {
                  case (e: Throwable) =>
                    setImplicitError(s"NO TAGKK for $t")
                    throw e
                }}}.tag")
    }
  }

}

object TagMacroImpl {
  final case class ImplicitSummon[C <: blackbox.Context with Singleton, U <: SingletonUniverse, DIU <: WithTags with Singleton](
    apply: U#Type => C#Expr[DIU#ScalaReflectTypeTag[_]]
  ) extends AnyVal
}





