package izumi.fundamentals.reflection

import izumi.fundamentals.platform.console.TrivialLogger
import izumi.fundamentals.reflection.ReflectionUtil.{Kind, kindOf}
import izumi.fundamentals.reflection.TagMacro._
import izumi.fundamentals.reflection.Tags.{HKTag, HKTagMaterializer, Tag}
import izumi.fundamentals.reflection.macrortti.{LightTypeTag, LightTypeTagMacro0}

import scala.annotation.implicitNotFound
import scala.collection.immutable.ListMap
import scala.reflect.api.Universe
import scala.reflect.macros.{TypecheckException, blackbox, whitebox}

// TODO: benchmark difference between running implicit search inside macro vs. return tree with recursive implicit macro expansion
// TODO: benchmark difference between searching all arguments vs. merge strategy
// TODO: benchmark ProviderMagnet vs. identity macro vs. normal function
class TagMacro(val c: blackbox.Context) {

  import c.universe._

  protected[this] val logger: TrivialLogger = TrivialMacroLogger.make[this.type](c, DebugProperties.`izumi.debug.macro.rtti`)
  private[this] val ltagMacro = new LightTypeTagMacro0[c.type](c)(logger)

  // workaround for a scalac bug - `Nothing` type is lost when two implicits for it are summoned from one implicit as in:
  //  implicit final def tagFromTypeTag[T](implicit t: TypeTag[T], l: LTag[T]): Tag[T] = Tag(t, l.fullLightTypeTag)
  // https://github.com/scala/bug/issues/11715
  final def makeTag[T: c.WeakTypeTag]: c.Expr[Tag[T]] = {
    val tpe = weakTypeOf[T]
    if (ReflectionUtil.allPartsStrong[c.universe.type](tpe.dealias)) {
      val ltag = ltagMacro.makeParsedLightTypeTagImpl(tpe)
      val cls = closestClass(tpe)
      c.Expr[Tag[T]] {
        q"_root_.izumi.fundamentals.reflection.Tags.Tag.apply[$tpe]($cls, $ltag)"
      }
    } else {
      makeTagImpl[T]
    }
  }

  @inline final def makeHKTagMaterializer[ArgStruct: c.WeakTypeTag]: c.Expr[HKTagMaterializer[ArgStruct]] = {
    c.Expr[HKTagMaterializer[ArgStruct]](q"new ${weakTypeOf[HKTagMaterializer[ArgStruct]]}(${makeHKTag[ArgStruct]})")
  }

  @inline final def makeHKTag[ArgStruct: c.WeakTypeTag]: c.Expr[HKTag[ArgStruct]] = {
    val argStruct = weakTypeOf[ArgStruct]
    if (ReflectionUtil.allPartsStrong[c.universe.type](argStruct)) {
      val ctor = ltagMacro.unpackArgStruct(argStruct)
      makeHKTagFromStrongTpe(ctor)
    } else {
      c.abort(c.enclosingPosition, s"Can't materialize HKTag[$argStruct]: found unresolved type parameters in $argStruct")
    }
  }

  private[this] def makeHKTagFromStrongTpe[ArgStruct](strongCtorType: Type) = {
    val ltag = ltagMacro.makeParsedLightTypeTagImpl(strongCtorType)
    val cls = closestClass(strongCtorType)
    c.Expr[HKTag[ArgStruct]] {
      q"_root_.izumi.fundamentals.reflection.Tags.HKTag.apply($cls, $ltag)"
    }
  }

  def makeTagImpl[T: c.WeakTypeTag]: c.Expr[Tag[T]] = {
    logger.log(s"Got compile tag: ${weakTypeOf[T]}")

    if (getImplicitError().endsWith(":")) { // yep
      logger.log(s"Got continuation implicit error: ${getImplicitError()}")
    } else {
      resetImplicitError()
      addImplicitError("\n\n<trace>: ")
    }

    val tgt = ReflectionUtil.norm(c.universe: c.universe.type)(weakTypeOf[T].dealias)

    addImplicitError(s"  deriving Tag for $tgt:")

    val res = tgt match {
      case RefinedType(intersection, _) =>
        mkRefined[T](intersection, tgt)
      case _ =>
        mkTagWithTypeParameters[T](tgt)
    }

    addImplicitError(s"  succeeded for: $tgt")

    logger.log(s"Final code of Tag[${weakTypeOf[T]}]:\n ${showCode(res.tree)}")

    res
  }

  @inline
  protected[this] def mkRefined[T: c.WeakTypeTag](intersection: List[Type], originalRefinement: Type): c.Expr[Tag[T]] = {
    val summonedIntersectionTags = intersection.map {
      t0 =>
        val t = ReflectionUtil.norm(c.universe: c.universe.type)(t0.dealias)
        summonLightTypeTagOfAppropriateKind(t)
    }
    val intersectionTags = c.Expr[List[LightTypeTag]](Liftable.liftList[c.Expr[LightTypeTag]].apply(summonedIntersectionTags))
    val structTag = mkStruct(originalRefinement)
    val cls = closestClass(originalRefinement)

    reify {
      Tag.refinedTag[T](cls.splice, intersectionTags.splice, structTag.splice)
    }
  }

  @inline
  protected[this] def mkStruct(originalRefinement: Type): c.Expr[LightTypeTag] = {
    originalRefinement.decls
      .find(_.info.typeSymbol.isParameter)
      .foreach {
        s =>
          val msg = s"  Encountered a type parameter ${s.info} as a part of structural refinement of $originalRefinement: It's not yet supported to summon a Tag for ${s.info} in that position!"

          addImplicitError(msg)
          c.abort(s.pos, getImplicitError())
      }

    // TODO: walk over members of struct and resolve Tags for them...
    ltagMacro.makeParsedLightTypeTagImpl(originalRefinement)
  }

  // we need to handle four cases – type args, refined types, type bounds and bounded wildcards(? check existence)
  @inline
  protected[this] def mkTagWithTypeParameters[T: c.WeakTypeTag](tpe: Type): c.Expr[Tag[T]] = {
    val constructorTag = {
      val ctor = tpe.typeConstructor
      getCtorKindIfCtorIsTypeParameter(ctor) match {
        // type constructor of this type is not a type parameter
        // AND not an intersection type
        // some of its arguments are type parameters that we should resolve
        case None =>
          logger.log(s"type A $ctor")
          makeHKTagFromStrongTpe(ctor)
//          ltagMacro.makeParsedLightTypeTagImpl(ctor)

        // error: the entire type is just a proper type parameter with no type arguments
        // it cannot be resolved further
        case Some(Kind(Nil)) =>
          logger.log(s"type B $ctor")
          val msg = s"  could not find implicit value for ${tagFormat(tpe)}: $tpe is a type parameter without an implicit Tag!"
          addImplicitError(msg)
          c.abort(c.enclosingPosition, getImplicitError())

        // type constructor is a type parameter AND has type arguments
        // we should resolve type constructor separately from an HKTag
        case Some(hktKind) =>
          logger.log(s"type C $ctor")
          summonHKTag(ctor, hktKind)
//          lttFromTag(summonHKTag(ctor, hktKind).tree)
      }
    }
    val argTags = {
      val args = tpe.typeArgs.map(t => ReflectionUtil.norm(c.universe: c.universe.type)(t.dealias))
      c.Expr[List[LightTypeTag]](Liftable.liftList[c.Expr[LightTypeTag]].apply(args.map(summonLightTypeTagOfAppropriateKind)))
    }

    reify {
      Tag.appliedTag[T](constructorTag.splice, argTags.splice)
    }
  }

  @inline
  private[this] final def closestClass(properTypeStrongCtor: Type): c.Expr[Class[_]] = {
    // unfortunately .erasure returns trash for intersection types
    val tpeLub = properTypeStrongCtor match {
      case r: RefinedTypeApi => lub(r.parents)
      case _ => properTypeStrongCtor
    }
    c.Expr[Class[_]](q"_root_.scala.Predef.classOf[${tpeLub.erasure}]")
  }

  @inline
  private[this] final def getCtorKindIfCtorIsTypeParameter(tpe: Type): Option[Kind] = {
    if (tpe.typeSymbol.isParameter) Some(kindOf(tpe))
    else None
  }

  protected[this] def mkTypeParameter(owner: Symbol, kind: Kind): Symbol = {
    import internal.reificationSupport._
    import internal.{polyType, typeBounds}

    val tpeSymbol = newNestedSymbol(owner, freshTypeName(""), NoPosition, Flag.PARAM | Flag.DEFERRED, isClass = false)

    val tpeTpe = if (kind.args.nonEmpty) {
      val params = kind.args.map(mkTypeParameter(tpeSymbol, _))

      polyType(params, typeBounds(definitions.NothingTpe, definitions.AnyTpe))
    } else {
      typeBounds(definitions.NothingTpe, definitions.AnyTpe)
    }

    setInfo(tpeSymbol, tpeTpe)

    tpeSymbol
  }

  @inline
  protected[this] def mkHKTagArgStruct(tpe: Type, kind: Kind): Type = {
    import internal.reificationSupport._

    val staticOwner = c.prefix.tree.symbol.owner

    logger.log(s"staticOwner: $staticOwner")

    val parents = List(definitions.AnyRefTpe)
    val mutRefinementSymbol: Symbol = newNestedSymbol(staticOwner, TypeName("<refinement>"), NoPosition, FlagsRepr(0L), isClass = true)

    val mutArg: Symbol = newNestedSymbol(mutRefinementSymbol, TypeName("Arg"), NoPosition, FlagsRepr(0L), isClass = false)
    val params = kind.args.map(mkTypeParameter(mutArg, _))
    setInfo(mutArg, mkPolyType(tpe, params))

    val scope = newScopeWith(mutArg)

    setInfo[Symbol](mutRefinementSymbol, RefinedType(parents, scope, mutRefinementSymbol))

    RefinedType(parents, scope, mutRefinementSymbol)
  }

  @inline
  protected[this] def mkPolyType(tpe: Type, params: List[c.Symbol]): Type = {
    val rhsParams = params.map(symbol => internal.typeRef(NoPrefix, symbol, Nil))

    internal.polyType(params, appliedType(tpe, rhsParams))
  }

  private[this] def summonLightTypeTagOfAppropriateKind(tpe: Type): c.Expr[LightTypeTag] = {
    lttFromTag(summonTagForKind(tpe, kindOf(tpe)))
  }

  private[this] def summonHKTag(tpe: Type, kind: Kind): c.Expr[HKTag[_]] = {
    c.Expr[HKTag[_]](summonTagForKind(tpe, kind))
  }

  @inline
  protected[this] def summonTagForKind(tpe: c.Type, kind: Kind): c.Tree = {
    try {
      if (kind == Kind(Nil)) {
        c.inferImplicitValue(appliedType(weakTypeOf[Tag[Nothing]].typeConstructor, tpe), silent = false)
      } else {
        val ArgStruct = mkHKTagArgStruct(tpe, kind)
        logger.log(s"Created impicit Arg: $ArgStruct")
        c.inferImplicitValue(appliedType(weakTypeOf[HKTag[Nothing]].typeConstructor, ArgStruct), silent = false)
      }
    } catch {
      case _: TypecheckException =>
        val error = hktagSummonHelpfulErrorMessage(tpe, kind)
        val msg = s"  could not find implicit value for ${tagFormat(tpe)}$error"
        addImplicitError(msg)
        c.abort(c.enclosingPosition, getImplicitError())
    }
  }

  @inline
  private[this] final def lttFromTag(tagTree: Tree): c.Expr[LightTypeTag] = {
    c.Expr[LightTypeTag](q"$tagTree.tag")
  }

  def getImplicitError(): String = {
    val annotations = symbolOf[Tag[Any]].annotations
    annotations.headOption.flatMap(
      AnnotationTools.findArgument(_) {
        case Literal(Constant(s: String)) => s
      }
    ).getOrElse(defaultTagImplicitError)
  }

  @inline
  protected[this] def addImplicitError(err: String): Unit = {
    setImplicitError(s"${getImplicitError()}\n$err")
  }

  @inline
  protected[this] def resetImplicitError(): Unit = {
    setImplicitError(defaultTagImplicitError)
  }

  @inline
  protected[this] def setImplicitError(err: String): Unit = {
    import internal.decorators._

    symbolOf[Tag[Any]].setAnnotations(
      Annotation(typeOf[implicitNotFound], List[Tree](Literal(Constant(err))), ListMap.empty)
    )
    ()
  }

  @inline
  private[this] final def hktagSummonHelpfulErrorMessage(tpe: Type, kind: Kind) = {
    tagFormatMap.get(kind) match {
      case Some(_) => ""
      case None =>
        val (typaramsWithKinds, appliedParams) = kind.args.zipWithIndex.map {
          case (k, i) =>
            val name = s"T${i + 1}"
            k.format(name) -> name
        }.unzip
        s"""
           |$tpe is of a kind $kind, which doesn't have a tag name. Please create a tag synonym as follows:
           |
           |  type TagXYZ[${kind.format(typeName = "K")}] = HKTag[ { type Arg[${typaramsWithKinds.mkString(", ")}] = K[${appliedParams.mkString(", ")}] } ]
           |
           |And use it in your context bound, as in def x[$tpe: TagXYZ] = ...
           |OR use Tag.auto.T macro, as in def x[$tpe: Tag.auto.T] = ...""".stripMargin
    }
  }

}

private object TagMacro {
  final val defaultTagImplicitError =
    "could not find implicit value for Tag[${T}]. Did you forget to put on a Tag, TagK or TagKK context bound on one of the parameters in ${T}? e.g. def x[T: Tag, F[_]: TagK] = ..."

  final def tagFormatMap: Map[Kind, String] = {
    Map(
      Kind(Nil) -> "Tag",
      Kind(Kind(Nil) :: Nil) -> "TagK",
      Kind(Kind(Nil) :: Kind(Nil) :: Nil) -> "TagKK",
      Kind(Kind(Nil) :: Kind(Nil) :: Kind(Nil) :: Nil) -> "TagK3",
      Kind(Kind(Kind(Nil) :: Nil) :: Nil) -> "TagT",
      Kind(Kind(Kind(Nil) :: Nil) :: Kind(Nil) :: Nil) -> "TagTK",
      Kind(Kind(Kind(Nil) :: Nil) :: Kind(Nil) :: Kind(Nil) :: Nil) -> "TagTKK",
      Kind(Kind(Kind(Nil) :: Nil) :: Kind(Nil) :: Kind(Nil) :: Kind(Nil) :: Nil) -> "TagTK3",
    )
  }

  final def tagFormat(tpe: Universe#Type): String = {
    val kind = kindOf(tpe)
    tagFormatMap.get(kind) match {
      case Some(t) => s"$t[$tpe]"
      case _ => s"HKTag for $tpe of kind $kind"
    }
  }
}

class TagLambdaMacro(override val c: whitebox.Context) extends TagMacro(c) {

  import c.universe._
  import c.universe.internal.decorators._

  def lambdaImpl: c.Tree = {
    val pos = c.macroApplication.pos

    val targetTpe = c.enclosingUnit.body.collect {
      case AppliedTypeTree(t, arg :: _) if t.exists(_.pos == pos) =>
        c.typecheck(arg, c.TYPEmode, c.universe.definitions.NothingTpe, silent = false, withImplicitViewsDisabled = true, withMacrosDisabled = true)
          .tpe
    }.headOption match {
      case None =>
        c.abort(c.enclosingPosition, "Couldn't find an the type that `Tag.auto.T` macro was applied to, please make sure you use the correct syntax, as in `def tagk[F[_]: Tag.auto.T]: TagK[T] = implicitly[Tag.auto.T[F]]`")
      case Some(t) =>
        t
    }

    val kind = kindOf(targetTpe)

    logger.log(s"Found posiition $pos, target type $targetTpe, target kind $kind")

    val ctorParam = mkTypeParameter(NoSymbol, kind)
    val ArgStruct = mkHKTagArgStruct(ctorParam.asType.toType, kind)

    val resultType = c.typecheck(
      tq"{ type T[${c.internal.typeDef(ctorParam)}] = _root_.izumi.fundamentals.reflection.Tags.HKTag[$ArgStruct] }"
      , c.TYPEmode, c.universe.definitions.NothingTpe, silent = false, withImplicitViewsDisabled = true, withMacrosDisabled = true
    ).tpe

    val res = Literal(Constant(())).setType(resultType)

    logger.log(s"final result: $resultType")

    res
  }
}
