package izumi.distage.constructors

import izumi.distage.model.definition.With
import izumi.fundamentals.platform.reflection.ReflectionUtil

import scala.annotation.{nowarn, tailrec}
import scala.quoted.{Expr, Quotes, Type}
import scala.collection.mutable
import izumi.distage.model.providers.{Functoid, FunctoidMacro}
import izumi.distage.model.providers.FunctoidMacro.FunctoidParametersMacro
import izumi.distage.model.reflection.Provider.{ProviderImpl, ProviderType}

class ConstructorContext[R: Type, Q <: Quotes, U <: ConstructorUtil[Q]](using val qctx: Q)(val util: U & ConstructorUtil[qctx.type]) {
  import qctx.reflect.*

  val resultTpe = TypeRepr.of[R].dealias.simplified
  val resultTpeTree = TypeTree.of[R]
  val resultTpeSym = resultTpe.typeSymbol

  val refinementMethods = util.unpackRefinement(resultTpe)

  val abstractMembers =
    resultTpeSym.fieldMembers
      .filter(
        m =>
          !m.flags.is(Flags.FieldAccessor) && !m.isLocalDummy && m.flags.is(Flags.Deferred) && !m.flags.is(Flags.Artifact) && !m.flags.is(Flags.Synthetic) && m.isValDef
      )
    ++ resultTpeSym.methodMembers
      .filter(m => m.flags.is(Flags.Method) && m.flags.is(Flags.Deferred) && !m.flags.is(Flags.Artifact) && !m.flags.is(Flags.Synthetic) && m.isDefDef)

  val abstractMethodsWithParams = abstractMembers.filter(m => m.flags.is(Flags.Method) && m.paramSymss.nonEmpty)
//    val refinementMethodsWithParams = refinementMethods.filter(_._2.paramTypes.nonEmpty)

  val parentsSymbols = util.findRequiredImplParents(resultTpeSym)
  val parentTypesParameterized = parentsSymbols.map(resultTpe.baseType)
  val constructorParamLists = parentTypesParameterized.map(t => t -> util.buildConstructorParameters(t))
  val flatCtorParams = constructorParamLists.flatMap(_._2.iterator.flatten)

  val methodDecls = abstractMembers.map(m => (m.name, m.flags.is(Flags.Method), Some(m), resultTpe.memberType(m))) ++ refinementMethods

  def isFactory: Boolean = abstractMembers.nonEmpty || refinementMethods.nonEmpty

  def isWireableTrait: Boolean = abstractMethodsWithParams.isEmpty
}

class ConstructorUtil[Q <: Quotes](using val qctx: Q) {
  import qctx.reflect.*

  private val withAnnotationSym: Symbol = TypeRepr.of[With].typeSymbol

  final case class ParamRepr(name: String, mbSymbol: Option[Symbol], tpe: TypeRepr)

  type ParamReprLists = List[List[ParamRepr]]

  def assertSignatureIsAcceptableForFactory(signatureParams: List[ParamRepr], resultTpe: TypeRepr, clue: String): Unit = {
    assert(signatureParams.groupMap(_.name)(_.tpe).forall(_._2.size == 1), "BUG: duplicated arg names!")

    val sigRevIndex = signatureParams.groupMap(_.tpe)(_.name)
    val duplications = sigRevIndex.filter(_._2.size > 1)

    if (duplications.nonEmpty) {
      import izumi.fundamentals.platform.strings.IzString.*
      val explanation = duplications.map((t, nn) => s"${t.show}: ${nn.mkString(", ")}").niceList()
//      report.errorAndAbort(s"Cannot build factory for ${resultTpe.show}, $clue contais contradicting arguments: $explanation")
      report.warning(s"Cannot build factory for ${resultTpe.show}, $clue contais contradicting arguments: $explanation")
    }

    sigRevIndex.view.mapValues(_.head).toMap
  }

  def requireConcreteTypeConstructor(tpe: TypeRepr, macroName: String): Unit = {
    if (!ReflectionUtil.intersectionUnionMembers(tpe).forall(t => ReflectionUtil.allPartsStrong(t.typeSymbol.typeRef))) {
      val hint = tpe.dealias.show
      report.errorAndAbort(
        s"""$macroName: Can't generate constructor for ${tpe.show}:
           |Type constructor is an unresolved type parameter `$hint`.
           |Did you forget to put a $macroName context bound on the $hint, such as [$hint: $macroName]?
           |""".stripMargin
      )
    }
  }

  def makeFunctoid[R: Type](params: List[ParamRepr], argsLambda: Expr[Seq[Any] => R], providerType: Expr[ProviderType]): Expr[Functoid[R]] = {
    val paramsMacro = new FunctoidParametersMacro[qctx.type]

    val paramDefs = params.map {
      case ParamRepr(n, s, t) => paramsMacro.makeParam(n, Right(t), s)
    }

    val out = '{
      new Functoid[R](
        new ProviderImpl[R](
          ${ Expr.ofList(paramDefs) },
          ${ paramsMacro.safeType[R] },
          ${ argsLambda },
          ${ providerType },
        )
      )
    }

    report.warning(
      s"""ConstructorUtil:fun=${argsLambda.show}
         |funType=${argsLambda.asTerm.tpe}
         |funSym=${argsLambda.asTerm.symbol}
         |funTypeSym=${argsLambda.asTerm.tpe.typeSymbol}
         |funTypeSymBases=${argsLambda.asTerm.tpe.baseClasses}
         |params=${params.map(p => s"$p:symbol-annos(${p.mbSymbol.map(s => s -> s.annotations)})")}
         |outputType=${Type.show[R]}
         |rawOutputType=(${TypeRepr.of[R]})
         |providerType=${providerType.show}
         |produced=${out.show}""".stripMargin
    )

    out
  }

  def wrapIntoFunctoidRawLambda[R: Type](
    params: List[ParamRepr]
  )(body: (Symbol, List[Term]) => Tree
  ): Expr[Seq[Any] => R] = {
    val mtpe = MethodType(List("args"))(
      _ => List(TypeRepr.of[Seq[Any]]),
      _ => TypeRepr.of[R],
    )
    Lambda(
      Symbol.spliceOwner,
      mtpe,
      {
        case (lamSym, (args: Term) :: Nil) =>
          val argRefs = params.iterator.zipWithIndex.map {
            case (ParamRepr(_, _, paramTpe), idx) =>
              paramTpe match {
                case ByNameUnwrappedTypeReprAsType('[t]) =>
                  '{ ${ args.asExprOf[Seq[Any]] }.apply(${ Expr(idx) }).asInstanceOf[() => t].apply() }.asTerm
                case TypeReprAsType('[t]) =>
                  '{ ${ args.asExprOf[Seq[Any]] }.apply(${ Expr(idx) }).asInstanceOf[t] }.asTerm
                case _ =>
                  report.errorAndAbort(s"Invalid higher-kinded type $paramTpe ${paramTpe.show}")
              }
          }.toList

          body(lamSym, argRefs)
      }: @nowarn("msg=match"),
    ).asExprOf[Seq[Any] => R]
  }

  def wrapCtorApplicationIntoFunctoidRawLambda[R: Type](paramss: ParamReprLists, constructorTerm: Term): Expr[Seq[Any] => R] = {
    wrapIntoFunctoidRawLambda[R](paramss.flatten) {
      (_, args0) =>
        import scala.collection.immutable.Queue
        val (_, argsLists) = paramss.foldLeft((args0, Queue.empty[List[Term]])) {
          case ((args, res), params) =>
            val (argList, rest) = args.splitAt(params.size)
            (rest, res :+ (argList: List[Tree]).asInstanceOf[List[Term]])
        }

        val appl = argsLists.foldLeft(constructorTerm)(_.appliedToArgs(_))
        Typed(appl, TypeTree.of[R])
    }
  }

  private object ByNameUnwrappedTypeReprAsType {
    def unapply(t: TypeRepr): Option[Type[?]] = {
      t match {
        case ByNameType(u) => Some(u.asType)
        case _ => None
      }
    }
  }

  object TypeReprAsType {
    def unapply(t: TypeRepr): Some[Type[?]] = {
      Some(t.asType)
    }
  }

  def unpackRefinement(t: TypeRepr): List[(String, Boolean, Option[Symbol], MethodOrPoly)] = {
    t match {
      case Refinement(parent, name, m: MethodOrPoly) =>
        (name, true, None, m) :: unpackRefinement(parent)
      case _ =>
        Nil
    }
  }

  def extractMethodTermParamLists(t0: TypeRepr, methodSym: Symbol): ParamReprLists = {
    val paramSymssExcTypes = methodSym.paramSymss.filterNot(_.headOption.exists(_.isTypeParam))

    def go(t: TypeRepr, paramSymss: List[List[Symbol]]): ParamReprLists = {
      t match {
        case mtpe @ MethodType(nn, tt, ret) =>
          nn.iterator
            .zip(tt)
            .zipAll(paramSymss match { case h :: _ => h; case _ => List.empty[Symbol] }, null, null.asInstanceOf[Symbol])
            .map {
              case (null, _) => null
              case ((n, t), maybeSymbol) => ParamRepr(n, Option(maybeSymbol), t)
            }.takeWhile(_ ne null).toList :: go(ret, paramSymss.drop(1))
        case PolyType(_, _, ret) =>
          go(ret, paramSymss)
        case _ =>
          List.empty
      }
    }

    go(t0, paramSymssExcTypes)
  }

  @tailrec
  final def returnTypeOfMethod(t: TypeRepr): TypeRepr = {
    t match {
      case MethodType(_, _, ret) =>
        returnTypeOfMethod(ret)
      case PolyType(_, _, ret) =>
        returnTypeOfMethod(ret)
      case r =>
        r
    }
  }

  final def ensureByName(tpe: TypeRepr): TypeRepr = {
    tpe match {
      case t @ ByNameType(_) => t
      case t => ByNameType(t)
    }
  }

  @tailrec final def returnTypeOfMethodOrByName(tpe: TypeRepr): TypeRepr = {
    tpe match {
      case ByNameType(t) =>
        returnTypeOfMethodOrByName(t)
      case MethodType(_, _, t) =>
        returnTypeOfMethodOrByName(t)
      case PolyType(_, _, t) =>
        returnTypeOfMethodOrByName(t)
      case t =>
        t
    }
  }

  def dereferenceTypeRef(tpe: TypeRepr): TypeRepr = {
    tpe match {
      case t: TypeRef =>
        t.typeSymbol.owner.typeRef.memberType(t.typeSymbol)
      case _ =>
        tpe
    }
  }

  def readWithAnnotation(tpe: TypeRepr): Option[TypeRepr] = {
    tpe match {
      case AnnotatedType(_, aterm) if aterm.tpe.baseClasses.contains(withAnnotationSym) =>
        aterm match {
          case Apply(TypeApply(Select(New(_), _), c :: _), _) =>
            Some(c.tpe)
          case _ =>
            report.errorAndAbort(s"distage.With annotation expects one type argument but got malformed tree ${aterm.show} ($aterm) : ${aterm.tpe}")
        }
      case _ =>
        None
    }
  }

  def findRequiredImplParents(resultTpeSym: Symbol): List[Symbol] = {
    if (!resultTpeSym.flags.is(Flags.Trait)) {
      List(resultTpeSym)
    } else {
      val banned = mutable.HashSet[Symbol](defn.ObjectClass, defn.MatchableClass, defn.AnyRefClass, defn.AnyValClass, defn.AnyClass)
      val seen = mutable.HashSet.empty[Symbol]
      seen.addAll(banned)

      def go(sym: Symbol): List[Symbol] = {
        val onlyBases = sym.typeRef.baseClasses
          .drop(1) // without own type
          .filterNot(seen)

        if (!sym.flags.is(Flags.Trait)) {
          // (abstract) class calls the constructors of its bases, so don't call constructors for any of its bases
          def banAll(s: Symbol): Unit = {
            val onlyBasesNotBanned = s.typeRef.baseClasses.drop(1).filterNot(banned)
            seen ++= onlyBasesNotBanned
            banned ++= onlyBasesNotBanned
            onlyBasesNotBanned.foreach(banAll)
          }

          banAll(sym)
          List(sym)
        } else {
          seen ++= onlyBases
          val needConstructorCall = onlyBases.filter(
            s =>
              !s.flags.is(Flags.Trait) || (
                s.primaryConstructor.paramSymss.nonEmpty
                && s.primaryConstructor.paramSymss.exists(_.headOption.exists(!_.isTypeParam))
              )
          )
          needConstructorCall ++ onlyBases.flatMap(go)
        }
      }

      val (classCtors0, traitCtors0) = go(resultTpeSym).filterNot(banned).distinct.partition(!_.flags.is(Flags.Trait))
      val classCtors = if (classCtors0.isEmpty) List(defn.ObjectClass) else classCtors0
      val traitCtors =
        // try to instantiate traits in order from deeper to shallower
        // (allow traits defined later in the hierarchy to override their base traits)
        (resultTpeSym :: traitCtors0).reverse
      classCtors ++ traitCtors
    }
  }

  def buildConstructorParameters(tpe: TypeRepr): ParamReprLists = {
    val ctorMethodTypeApplied =
      try {
        tpe.memberType(tpe.typeSymbol.primaryConstructor).appliedTo(tpe.typeArgs)
      } catch {
        case t: Throwable =>
          throw new RuntimeException(s"Got $t in tpe=${tpe.show} prim=${tpe.typeSymbol.primaryConstructor}, pt=${tpe.typeSymbol.primaryConstructor.typeRef}")
      }

    extractMethodTermParamLists(ctorMethodTypeApplied, tpe.typeSymbol.primaryConstructor)
  }

  def buildConstructorApplication(resultType: TypeRepr): Term = {
    resultType.typeSymbol.primaryConstructor match {
      case s if s.isNoSymbol =>
        report.errorAndAbort(s"Cannot find primary constructor in $resultType")
      case consSym =>
        val ctorTree = Select(New(TypeTree.of(using resultType.asType)), consSym)
        ctorTree.appliedToTypeTrees(resultType.typeArgs.map(tArg => TypeTree.of(using tArg.asType)))
    }
  }

  def buildParentConstructorCallTerms(
    resultTpe: TypeRepr,
    constructorParamListsRepr: List[(TypeRepr, ParamReprLists)],
    contextParameters: List[Term],
  ): Seq[Term] = {
    import scala.collection.immutable.Queue
    val (_, parents) = constructorParamListsRepr.foldLeft((contextParameters, Queue.empty[Term])) {
      case ((remainingLamArgs, doneCtors), (parentType, ctorParamListsRepr)) =>
        val ctorTreeParameterized = buildConstructorApplication(parentType)
        val (rem, argsLists) = ctorParamListsRepr.foldLeft((remainingLamArgs, Queue.empty[List[Term]])) {
          case ((lamArgs, res), params) =>
            val (argList, rest) = lamArgs.splitAt(params.size)
            (rest, res :+ argList)
        }

        val appl = argsLists.foldLeft(ctorTreeParameterized)(_.appliedToArgs(_))
        (rem, doneCtors :+ appl)
    }

    parents
  }
}
