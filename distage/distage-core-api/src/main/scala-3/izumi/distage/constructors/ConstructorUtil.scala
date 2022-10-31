package izumi.distage.constructors

import izumi.distage.model.definition.With
import izumi.fundamentals.platform.reflection.ReflectionUtil

import scala.annotation.{nowarn, tailrec}
import scala.quoted.{Expr, Quotes, Type}
import scala.collection.mutable
import izumi.distage.model.providers.{Functoid, FunctoidMacro}
import izumi.distage.model.providers.FunctoidMacro.FunctoidParametersMacro
import izumi.distage.model.reflection.Provider.{ProviderImpl, ProviderType}

class ConstructorContext[R: Type, Q <: Quotes](using val qctx: Q)(val util: ConstructorUtil[qctx.type]) {
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

  val methodDecls = abstractMembers.map(m => (m.name, m.flags.is(Flags.Method), resultTpe.memberType(m))) ++ refinementMethods

  def isFactory: Boolean = abstractMembers.nonEmpty || refinementMethods.nonEmpty

  def isWireableTrait: Boolean = abstractMethodsWithParams.isEmpty
}

class ConstructorUtil[Q <: Quotes](using val qctx: Q) {
  import qctx.reflect.*

  private val withAnnotationSym: Symbol = TypeRepr.of[With].typeSymbol

  type ParamReprList = List[(String, TypeRepr)]
  type ParamReprLists = List[ParamReprList]
  type ParamTreeList = List[(String, TypeTree)]
  type ParamTreeLists = List[ParamTreeList]

  extension (params: ParamReprList) {
    def toTrees: ParamTreeList = params.map((n, t) => (n, TypeTree.of(using t.asType))).toList
  }

  def assertSignatureIsAcceptableForFactory(signatureParams: ParamReprList, resultTpe: TypeRepr, clue: String): Unit = {
    assert(signatureParams.groupMap(_._1)(_._2).forall(_._2.size == 1), "BUG: duplicated arg names!")

    val sigRevIndex = signatureParams.groupMap(_._2)(_._1)
    val duplications = sigRevIndex.filter(_._2.size > 1)

    if (duplications.nonEmpty) {
      import izumi.fundamentals.platform.strings.IzString.*
      val explanation = duplications.map((t, nn) => s"${t.show}: ${nn.mkString(", ")}").niceList()
//      report.errorAndAbort(s"Cannot build factory for ${resultTpe.show}, $clue contais contradicting arguments: $explanation")
      report.warning(s"Cannot build factory for ${resultTpe.show}, $clue contais contradicting arguments: $explanation")
    }

    sigRevIndex.view.mapValues(_.head).toMap
  }

  def requireConcreteTypeConstructor[R: Type](macroName: String): Unit = {
    requireConcreteTypeConstructor(TypeRepr.of[R], macroName)
  }

  def requireConcreteTypeConstructor(tpe: TypeRepr, macroName: String): Unit = {
    if (!ReflectionUtil.allPartsStrong(tpe.typeSymbol.typeRef)) {
      val hint = tpe.dealias.show
      report.errorAndAbort(
        s"""$macroName: Can't generate constructor for ${tpe.show}:
           |Type constructor is an unresolved type parameter `$hint`.
           |Did you forget to put a $macroName context bound on the $hint, such as [$hint: $macroName]?
           |""".stripMargin
      )
    }
  }

  def makeFunctoid[R: Type](params: ParamReprList, argsLambda: Expr[Seq[Any] => R], providerType: Expr[ProviderType]): Expr[Functoid[R]] = {
    val paramsMacro = new FunctoidParametersMacro[qctx.type]

    val paramDefs = paramsMacro.makeParams[R](params.map((n, t) => (n, None, Right(t))))

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
      s"""fun=${argsLambda.show}
         |funType=${argsLambda.asTerm.tpe}
         |funSym=${argsLambda.asTerm.symbol}
         |funTypeSym=${argsLambda.asTerm.tpe.typeSymbol}
         |funTypeSymBases=${argsLambda.asTerm.tpe.baseClasses}
         |outputType=${Type.show[R]}
         |rawOutputType=(${TypeRepr.of[R]})
         |produced=${out.show}""".stripMargin
    )

    out
  }

  def wrapIntoFunctoidRawLambda[R: Type](
    params: ParamReprList
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
            case ((_, paramTpe), idx) =>
              paramTpe match {
                case ByNameUnwrappedAsType('[t]) =>
                  '{ ${ args.asExprOf[Seq[Any]] }.apply(${ Expr(idx) }).asInstanceOf[() => t].apply() }.asTerm
                case AsType('[t]) =>
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

  private object ByNameUnwrappedAsType {
    def unapply(t: TypeRepr): Option[Type[?]] = {
      t match {
        case ByNameType(u) => Some(u.asType)
        case _ => None
      }
    }
  }

  private object AsType {
    def unapply(t: TypeRepr): Some[Type[?]] = {
      Some(t.asType)
    }
  }

  def unpackRefinement(t: TypeRepr): List[(String, Boolean, MethodOrPoly)] = {
    t match {
      case Refinement(parent, name, m: MethodOrPoly) =>
        List((name, true, m)) ++ unpackRefinement(parent)
      case _ =>
        List.empty
    }
  }

  def flattenMethodSignature(t: TypeRepr): ParamReprList = {
    t match {
      case MethodType(nn, tt, ret) =>
        nn.zip(tt) ++ flattenMethodSignature(ret)
      case PolyType(nn, tt, ret) =>
        nn.zip(tt) ++ flattenMethodSignature(ret)
      case _ =>
        List.empty
    }
  }

  @tailrec
  final def realReturnType(t: TypeRepr): TypeRepr = {
    t match {
      case MethodType(_, _, ret) =>
        realReturnType(ret)
      case PolyType(_, _, ret) =>
        realReturnType(ret)
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

  @tailrec final def dropMethodType(tpe: TypeRepr): TypeRepr = {
    tpe match {
      case MethodType(_, _, t) =>
        dropMethodType(t)
      case PolyType(_, _, t) =>
        dropMethodType(t)
      case t =>
        t
    }
  }

//  final def dropAnno(tpe: TypeRepr): TypeRepr = {
//    tpe match {
//      case AnnotatedType(t, _) =>
//        t
//      case t => t
//    }
//  }

  @tailrec final def dropWrappers(tpe: TypeRepr): TypeRepr = {
    tpe match {
      case ByNameType(t) =>
        dropWrappers(t)
      case MethodType(_, _, t) =>
        dropWrappers(t)
      case PolyType(_, _, t) =>
        dropWrappers(t)
      case t =>
        t
    }
  }

  def dropTypeRef(tpe: TypeRepr): TypeRepr = {
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

    def go(t: TypeRepr): ParamReprLists = {
      t match {
        case MethodType(paramNames, paramTpes, res) =>
          paramNames.zip(paramTpes) :: go(res)
        case _ =>
          Nil
      }
    }

    go(ctorMethodTypeApplied)
  }

  def buildConstructorApplication(baseType: TypeRepr): Term = {
    Some(baseType.typeSymbol.primaryConstructor).filterNot(_.isNoSymbol) match {
      case Some(consSym) =>
        val ctorTree = Select(New(TypeTree.of(using baseType.asType)), consSym)
        ctorTree.appliedToTypeTrees(baseType.typeArgs.map(repr => TypeTree.of(using repr.asType)))
      case None =>
        report.errorAndAbort(s"Cannot find primary constructor in $baseType")
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
