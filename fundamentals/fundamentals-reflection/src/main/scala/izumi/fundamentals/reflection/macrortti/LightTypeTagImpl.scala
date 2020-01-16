package izumi.fundamentals.reflection.macrortti

import izumi.fundamentals.collections.IzCollections._
import izumi.fundamentals.platform.console.TrivialLogger
import izumi.fundamentals.platform.strings.IzString._
import izumi.fundamentals.reflection.macrortti.LightTypeTagImpl.{Broken, globalCache}
import izumi.fundamentals.reflection.macrortti.LightTypeTagRef.RefinementDecl.TypeMember
import izumi.fundamentals.reflection.macrortti.LightTypeTagRef.SymName.{SymLiteral, SymTermName, SymTypeName}
import izumi.fundamentals.reflection.macrortti.LightTypeTagRef._
import izumi.fundamentals.reflection.{DebugProperties, SingletonUniverse}

import scala.annotation.tailrec
import scala.collection.mutable
import scala.reflect.api.Universe

object LightTypeTagImpl {
  private lazy val globalCache = new java.util.WeakHashMap[Any, AbstractReference]

  /** caching is enabled by default for runtime light type tag creation */
  private[this] lazy val runtimeCacheEnabled: Boolean = {
    System.getProperty(DebugProperties.`izumi.rtti.cache.runtime`).asBoolean()
      .getOrElse(true)
  }

  /** Create a LightTypeTag at runtime for a reflected type */
  def makeLightTypeTag(u: Universe)(typeTag: u.Type): LightTypeTag = {
    ReflectionLock.synchronized {
      val logger = TrivialLogger.make[this.type](DebugProperties.`izumi.debug.macro.rtti`)
      new LightTypeTagImpl[u.type](u, withCache = runtimeCacheEnabled, logger).makeFullTagImpl(typeTag)
    }
  }

  private[this] object ReflectionLock

  sealed trait Broken[T, S] {
    def toSet: Set[T]
  }
  object Broken {
    final case class Single[T, S](t: T) extends Broken[T, S] {
      override def toSet: Set[T] = Set(t)
    }
    final case class Compound[T, S](tpes: Set[T], decls: Set[S]) extends Broken[T, S] {
      override def toSet: Set[T] = tpes
    }
  }
}

final class LightTypeTagImpl[U <: SingletonUniverse](val u: U, withCache: Boolean, logger: TrivialLogger) {

  import u._

  @inline private[this] final val any = definitions.AnyTpe
  @inline private[this] final val obj = definitions.ObjectTpe
  @inline private[this] final val nothing = definitions.NothingTpe
  @inline private[this] final val ignored = Set(any, obj, nothing)

  @inline private[this] final val it = u.asInstanceOf[scala.reflect.internal.Types]

  def makeFullTagImpl(tpe0: Type): LightTypeTag = {
    val tpe = norm(tpe0.dealias).dealias
    val lttRef = makeRef(tpe)

    val allReferenceComponents: Set[Type] = Set(tpe) ++ {
      allTypeReferences(tpe)
        .flatMap(UniRefinement.breakRefinement(_).toSet)
    }

    val stableBases = makeStableBases(tpe, allReferenceComponents)
    val basesAsLambdas = allReferenceComponents.flatMap(makeBaseClasses)
    val allBases = Seq(
      basesAsLambdas,
      stableBases,
    )
    val fullDb = allBases
      .flatten
      .toMultimap
      .filterNot(_._2.isEmpty)

    val unappliedDb = makeUnappliedInheritanceDb(allReferenceComponents)

    LightTypeTag(lttRef, fullDb, unappliedDb)
  }

  private def makeUnappliedInheritanceDb(allReferenceComponents: Set[Type]): Map[NameReference, Set[NameReference]] = {
    val baseclassReferences = allReferenceComponents.flatMap {
      i =>
        val allbases = tpeBases(i).filterNot(_.takesTypeArgs)
        allbases.map(b => (i, makeRef(b)))
    }

    val unparameterizedInheritanceData = baseclassReferences.flatMap {
      case (i, ref) =>
        val tpef = norm(i.dealias.resultType)
        val prefix = getPrefix(tpef)
        val targetRef = makeNameReference(i, tpef.typeSymbol, Boundaries.Empty, prefix)

        val srcname = i match {
          case a: TypeRefApi =>
            val srcname = symName(a.sym)
            if (srcname != targetRef.ref) {
              Seq((NameReference(srcname, Boundaries.Empty, prefix), targetRef))
            } else {
              Seq.empty
            }
          case _ =>
            Seq.empty
        }

        srcname ++ Seq((targetRef, ref))
    }
      .toMultimap
      .map {
        case (t, parents) =>
          t -> parents
            .collect {
              case r: AppliedNamedReference =>
                r.asName
            }
            .filterNot(_ == t)
      }
      .filterNot(_._2.isEmpty)

    unparameterizedInheritanceData
  }

  private def makeStableBases(tpe: Type, allReferenceComponents: Set[Type]): Set[(AbstractReference, AbstractReference)] = {
    val baseclassReferences = allReferenceComponents.flatMap {
      i =>
        val appliedBases = tpeBases(i).filterNot(_.takesTypeArgs)

        appliedBases.map {
          b =>
            val targs = makeLambdaParams(None, tpe.etaExpand.typeParams).toMap
            val out = makeRef(b, targs, forceLam = targs.nonEmpty) match {
              case l: Lambda =>
                if (l.allArgumentsReferenced) {
                  l
                } else {
                  l.output
                }
              case reference: AppliedReference =>
                reference
            }
            (i, out)
        }
    }

    val stableBases = baseclassReferences.map {
      case (b, p) =>
        makeRef(b) -> p
    }
    stableBases
  }

  private def makeBaseClasses(tpe: Type): Seq[(AbstractReference, AbstractReference)] = {
    def makeBaseLambdas(tpe: Type): Seq[AbstractReference] = {
      val basetypes = tpe.baseClasses
        .map(tpe.baseType)
        .filterNot(_.typeSymbol.fullName == tpe.typeSymbol.fullName)

      val targs = tpe.etaExpand.typeParams

      val lambdas = if (targs.nonEmpty) {
        basetypes.flatMap {
          base =>

            val lamParams = makeLambdaParams(None, targs)
            val reference = makeRef(base, lamParams.toMap)

            reference match {
              case l: Lambda =>
                Seq(l)
              case reference: AppliedReference =>
                Seq(Lambda(lamParams.map(_._2), reference))
                  .filter(_.allArgumentsReferenced)
            }
        }
      } else {
        Seq.empty
      }
      lambdas
    }

    val unref = UniRefinement.breakRefinement(tpe)

    val out = unref
      .toSet
      .flatMap {
        base =>
          val baseAsLambda = if (base.takesTypeArgs) {
            base
          } else {
            base.etaExpand
          }
          val tref = makeRef(baseAsLambda)
          val baseLambdas = makeBaseLambdas(baseAsLambda)
          val mappedLambdas = baseLambdas
            .collect {
              case l: Lambda =>
                (tref, l)
            }
          mappedLambdas
      }
      .toSeq

    out
  }

  private def allTypeReferences(tpe0: Type): Set[Type] = {

    def extract(tpe: Type, inh: mutable.HashSet[Type]): Unit = {
      val current = Seq(tpe, tpe.dealias.resultType)
      inh ++= current

      // we need to use tpe.etaExpand but 2.13 has a bug: https://github.com/scala/bug/issues/11673#
      // tpe.etaExpand.resultType.dealias.typeArgs.flatMap(_.dealias.resultType.typeSymbol.typeSignature match {
      val more = tpe.dealias.resultType.typeArgs.flatMap(t => norm(t.dealias.resultType.typeSymbol.typeSignature) match {
        case t: TypeBoundsApi =>
          Seq(t.hi, t.lo)
        case _ =>
          Seq.empty
      })
//      val prefix = getPre(tpe)

      val next = (tpe.typeArgs ++ tpe.dealias.resultType.typeArgs ++ more).filterNot(inh.contains)
      next.foreach(a => extract(a, inh))
    }

    val inh = mutable.HashSet[Type]()
    extract(tpe0, inh)
    inh.toSet
  }

  private def tpeBases(tpe: Type): Seq[Type] = {
    val tpef = tpe.dealias.resultType
    val higherBases = tpef.baseClasses
    val parameterizedBases = higherBases
      .filterNot {
        s =>
          val btype = s.asType.toType
          ignored.exists(_ =:= btype) || btype =:= tpef
      }
      .map(s => tpef.baseType(s))

    val allbases = parameterizedBases.filterNot(_ =:= tpef)
    allbases
  }

  private def makeRef(tpe: Type): AbstractReference = {
    if (withCache) {
      globalCache.synchronized(globalCache.get(tpe)) match {
        case null =>
          val ref = makeRef(tpe, Map.empty)
          globalCache.synchronized(globalCache.put(tpe, ref))
          ref
        case value =>
          value
      }
    } else {
      makeRef(tpe, Map.empty)
    }
  }

  private def makeRef(tpe: Type, terminalNames: Map[String, LambdaParameter], forceLam: Boolean = false): AbstractReference = {
    makeRef(0)(tpe, Set(tpe), terminalNames, forceLam)
  }

  private def makeRef(level: Int)(tpe: Type, path: Set[Type], terminalNames: Map[String, LambdaParameter], forceLam: Boolean): AbstractReference = {
    val thisLevel = logger.sub(level)

    def sub(tpe: Type, stop: Map[String, LambdaParameter] = Map.empty): AbstractReference = {
      this.makeRef(level + 1)(tpe, path + tpe, terminalNames ++ stop, forceLam = false)
    }

    def makeBoundaries(t: Type): Boundaries = {
      t.typeSymbol.typeSignature match {
        case b: TypeBoundsApi =>
          if ((b.lo =:= nothing && b.hi =:= any) || (path.contains(b.lo) || path.contains(b.hi))) {
            Boundaries.Empty
          } else {
            Boundaries.Defined(sub(b.lo), sub(b.hi))
          }
        case _ =>
          Boundaries.Empty
      }
    }

    def makeLambda(t: Type): AbstractReference = {
      val asPoly = t.etaExpand
      val result = asPoly.resultType.dealias

      val targs = asPoly.typeParams
      val ctxId = if (level > 0) {
        Some(level.toString)
      } else {
        None
      }
      val lamParams = makeLambdaParams(ctxId, targs)

      thisLevel.log(s"✴️ λ type $t has parameters $lamParams, terminal names = $terminalNames")
      val reference = sub(result, lamParams.toMap)
      val out = Lambda(lamParams.map(_._2), reference)
      if (!out.allArgumentsReferenced) {
        val kvParams = lamParams.map { case (k, v) => s"$v = $k" }
        thisLevel.log(s"⚠️ unused 𝝺 args! type $t => $out, context: $terminalNames, 𝝺 params: $kvParams, 𝝺 result: $result => $reference, referenced: ${out.referenced} ")
      }

      thisLevel.log(s"✳️ Restored $t => $out")
      out
    }

    def unpack(t: Type, rules: Map[String, LambdaParameter]): AppliedNamedReference = {
      val tpef = norm(t.dealias.resultType)
      val prefix = getPrefix(tpef)
      val typeSymbol = tpef.typeSymbol
      val boundaries = makeBoundaries(tpef)
      val nameref = rules.get(typeSymbol.fullName) match {
        case Some(value) =>
          // this is a previously encountered type variable
          NameReference(SymTypeName(value.name), boundaries, prefix)

        case None =>
          makeNameReference(t, typeSymbol, boundaries, prefix)
      }

      tpef.typeArgs match {
        case Nil =>
          nameref

        case args =>
          val params = args.zip(t.dealias.typeConstructor.typeParams).map {
            case (a, pa) =>
              TypeParam(sub(a), getVariance(pa.asType))
          }
          FullReference(nameref.ref.name, params, prefix)
      }
    }

    def unpackRefined(t: Type, rules: Map[String, LambdaParameter]): AppliedReference = {
      UniRefinement.breakRefinement(t) match {
        case Broken.Compound(tpes, decls) =>
          val parts = tpes.map(p => unpack(p, rules))
          val intersection = IntersectionReference(parts)

          if (decls.nonEmpty) {
            Refinement(intersection, UniRefinement.convertDecls(decls.toList, rules).toSet)
          } else {
            intersection
          }

        case _ =>
          // we intentionally ignore breakRefinement result here, it breaks lambdas
          unpack(t.dealias.resultType, rules)
      }
    }

    val out = tpe match {
      case p if forceLam =>
        Lambda(terminalNames.values.toList, unpackRefined(p, terminalNames))

      case _: PolyTypeApi =>
        // PolyType is not a type, we have to use tpe
        makeLambda(tpe)
      case p if p.takesTypeArgs =>

        if (terminalNames.contains(p.typeSymbol.fullName)) {
          unpackRefined(p, terminalNames)
        } else {
          makeLambda(p)
        }

      case c =>
        unpackRefined(c, terminalNames)
    }

    out
  }

  private def makeLambdaParams(ctxid: Option[String], targs: List[Symbol]): List[(String, LambdaParameter)] = {
    targs.zipWithIndex.map {
      case (targ, idx) =>
        val name = ctxid match {
          case Some(value) =>
            s"$value:${idx.toString}"
          case None =>
            idx.toString
        }

        targ.fullName -> LambdaParameter(name)
    }
  }

  object UniRefinement {
    def unapply(tpef: u.Type): Option[(List[Type], List[SymbolApi])] = {
      (tpef: AnyRef) match {
        case x: it.RefinementTypeRef =>
          Some((x.parents.map(_.asInstanceOf[Type]), x.decls.toList.asInstanceOf[List[SymbolApi]]))
        case r: u.RefinedTypeApi@unchecked =>
          Some((r.parents, r.decls.toList))
        case _ =>
          None
      }
    }

    def convertDecls(decls: List[SymbolApi], terminalNames: Map[String, LambdaParameter]): List[RefinementDecl] = {
      decls.flatMap {
        decl =>
          if (decl.isMethod) {
            val m = decl.asMethod
            val ret = m.returnType

            val params = m.paramLists.map {
              paramlist =>
                paramlist.map {
                  p =>
                    val pt = p.typeSignature
                    makeRef(pt, terminalNames).asInstanceOf[AppliedReference]
                }
            }

            val inputs = if (params.nonEmpty) {
              params
            } else {
              Seq(Seq.empty)
            }

            inputs.map {
              pl =>
                RefinementDecl.Signature(m.name.decodedName.toString, pl.toList, makeRef(ret, terminalNames).asInstanceOf[AppliedReference])
            }
          } else if (decl.isType) {
            val tpe = if (decl.isAbstract) {
              decl.asType.toType
            } else {
              decl.typeSignature
            }
            val ref = makeRef(tpe, terminalNames)
            Seq(TypeMember(decl.name.decodedName.toString, ref))
          } else {
            None
          }
      }
    }

    def breakRefinement(t: Type): Broken[Type, SymbolApi] = {
      breakRefinement0(t) match {
        case (t, d) if d.isEmpty && t.size == 1 =>
          Broken.Single(t.head)
        case (t, d) =>
          Broken.Compound(t, d)
      }
    }

    private def breakRefinement0(t: Type): (Set[Type], Set[SymbolApi]) = {
      fullDealias(t) match {
        case UniRefinement(parents, decls) =>
          val parts = parents.map(breakRefinement0)
          val types = parts.flatMap(_._1)
          val d = parts.flatMap(_._2)
          (types.toSet, (decls ++ d).toSet)
        case t =>
          (Set(t), Set.empty)

      }
    }
  }

  private def getPrefix(tpef: u.Type): Option[AppliedReference] = {
    def fromRef(o: Type): Option[AppliedReference] = {
      makeRef(o) match {
        case a: AppliedReference =>
          Some(a)
        case o =>
          throw new IllegalStateException(s"Cannot extract prefix from $tpef: expected applied reference, but got $o")
      }
    }

    def unpackPrefix(pre: Type) = {
      pre match {
        case i if i.typeSymbol.isPackage =>
          None
        case k if k == NoPrefix =>
          None
        case k: ThisTypeApi =>
          k.sym.asType.toType match {
            // This case matches UniRefinement.unapply#it.RefinementTypeRef case
            case UniRefinement(_, _) =>
              None
            case o =>
              fromRef(o)
          }
        case k if k.termSymbol != NoSymbol =>
          Some(NameReference(symName(k.termSymbol), Boundaries.Empty, getPrefix(k.termSymbol.typeSignature)))
        case o =>
          fromRef(o)
      }
    }

    getPre(tpef).flatMap(unpackPrefix)
  }

  private def getPre(tpe: Type): Option[Type] = {
    tpe match {
      case t: TypeRefApi => Some(t.pre).filterNot(_ == NoPrefix)
      case t: SingleTypeApi => Some(t.pre).filterNot(_ == NoPrefix)
      case _ => None
    }
  }

  private def makeNameReference(originalType: Type, typeSymbol: Symbol, boundaries: Boundaries, prefix: Option[AppliedReference]): NameReference = {
    originalType match {
      case c: ConstantTypeApi =>
        NameReference(SymLiteral(c.value.value), boundaries, prefix)
      case s: SingleTypeApi if s.sym != NoSymbol =>
        NameReference(symName(s.sym),  boundaries, prefix)
      case _ =>
        NameReference(symName(typeSymbol), boundaries, prefix)
    }
  }

  private def symName(sym: u.Symbol): SymName = {
    val base = sym.fullName
    if (sym.isTerm || sym.isModuleClass || sym.typeSignature.isInstanceOf[u.SingletonTypeApi]) {
      SymTermName(base)
    } else {
      SymTypeName(base)
    }
  }

  private def fullDealias(t: u.Type): u.Type = {
    if (t.takesTypeArgs) {
      t.etaExpand.dealias.resultType.dealias.resultType
    } else {
      t.dealias.resultType
    }
  }

  /** Mini `normalize`. We don't wanna do scary things such as beta-reduce. And AFAIK the only case that can make us
    * confuse a type-parameter for a non-parameter is an empty refinement `T {}`. So we just strip it when we get it. */
  @tailrec
  protected[this] final def norm(x: Type): Type = {
    x match {
      case RefinedType(t :: Nil, m) if m.isEmpty =>
        logger.log(s"Stripped empty refinement of type $t. member scope $m")
        norm(t)
      case AnnotatedType(_, t) => norm(t)
      case _ => x
    }
  }

  private def getVariance(tpes: TypeSymbol): Variance = {
    if (tpes.isCovariant) {
      Variance.Covariant
    } else if (tpes.isContravariant) {
      Variance.Contravariant
    } else {
      Variance.Invariant
    }
  }
}
