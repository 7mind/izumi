package izumi.distage.constructors

import izumi.fundamentals.platform.reflection.ReflectionUtil

import scala.annotation.tailrec
import scala.quoted.{Expr, Quotes, Type}
import scala.collection.mutable
import izumi.distage.model.providers.FunctoidMacro

class ConstructorContext[R: Type, Q <: Quotes](using val qctx: Q)(val util: ConstructorUtil[qctx.type]) {
  import qctx.reflect.*

  val resultTpe = TypeRepr.of[R].dealias.simplified
  val resultTypeTree = TypeTree.of[R]
  val resultTpeSym = resultTpe.typeSymbol

  val refinementMethods = util.unpackRefinement(resultTpe)

  val abstractMethods = resultTpeSym.methodMembers
    .filter(m => m.flags.is(Flags.Method) && m.flags.is(Flags.Deferred) && !m.flags.is(Flags.Artifact) && m.isDefDef)

  // TODO: handle refinements?
  val abstractMethodsWithParams = abstractMethods.filter(_.paramSymss.nonEmpty)

  val parentsSymbols = util.findRequiredImplParents(resultTpeSym)
  val constructorParamLists = parentsSymbols.map(util.buildConstructorParameters(resultTpe))
  val flatCtorParams = constructorParamLists.flatMap(_._2.iterator.flatten)

  val methodDecls = abstractMethods.map(m => (m.name, m.owner.typeRef.memberType(m))) ++ refinementMethods

  def isFactory: Boolean = abstractMethods.nonEmpty || refinementMethods.nonEmpty

  def isWireableTrait: Boolean = abstractMethodsWithParams.isEmpty
}

class ConstructorUtil[Q <: Quotes](using val qctx: Q) {
  import qctx.reflect.*

  type ParamListRepr = List[(String, qctx.reflect.TypeRepr)]
  type ParamListsRepr = List[ParamListRepr]
  type ParamListTree = List[(String, qctx.reflect.TypeTree)]
  type ParamListsTree = List[ParamListTree]

  implicit class ParamListExt(params: ParamListRepr) {
    def toTrees: ParamListTree = params.map((n, t) => (n, TypeTree.of(using t.asType))).toList
  }

  def assertSignatureIsAcceptableForFactory(signatureParams: ParamListRepr, resultTpe: TypeRepr, clue: String): Unit = {
    assert(signatureParams.groupMap(_._1)(_._2).forall(_._2.size == 1), "BUG: duplicated arg names!")

    val sigRevIndex = signatureParams.groupMap(_._2)(_._1)
    val duplications = sigRevIndex.filter(_._2.size > 1)

    if (duplications.nonEmpty) {
      import izumi.fundamentals.platform.strings.IzString.*
      val explanation = duplications.map((t, nn) => s"${t.show}: ${nn.mkString(", ")}").niceList()
      report.errorAndAbort(s"Cannot build factory for ${resultTpe.show}, $clue contais contradicting arguments: $explanation")
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

  def wrapApplicationIntoLambda[R: Type](paramss: ParamListsTree, constructorTerm: qctx.reflect.Term): Expr[Any] = {
    wrapIntoLambda[R](paramss) {
      (_, args0) =>
        import scala.collection.immutable.Queue
        val (_, argsLists) = paramss.foldLeft((args0, Queue.empty[List[Term]])) {
          case ((args, res), params) =>
            val (argList, rest) = args.splitAt(params.size)
            (rest, res :+ (argList: List[Tree]).asInstanceOf[List[Term]])
        }

        val appl = argsLists.foldLeft(constructorTerm)(_.appliedToArgs(_))
        val trm = Typed(appl, TypeTree.of[R])
        trm
    }
  }

  def wrapIntoLambda[R: Type](
    paramss: ParamListsTree
  )(body: (qctx.reflect.Symbol, List[qctx.reflect.Term]) => qctx.reflect.Tree
  ): Expr[Any] = {
    val params = paramss.flatten
    val mtpe = MethodType(params.map(_._1))(
      _ => params.map(t => t._2.tpe),
      _ => TypeRepr.of[R],
    )
    val lam = Lambda(
      Symbol.spliceOwner,
      mtpe,
      {
        case (lamSym, args0) =>
          body(lamSym, args0.asInstanceOf[List[Term]])
      },
    )
    lam.asExpr
  }

  def unpackRefinement(t: TypeRepr): List[(String, MethodType)] = {
    t match {
      case Refinement(parent, name, m: MethodType) =>
        List((name, m)) ++ unpackRefinement(parent)
      case o =>
        List.empty
    }
  }

  def flattenMethodSignature(t: TypeRepr): ParamListRepr = {
    t match {
      case MethodType(nn, tt, ret) =>
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

  final def dropMethodType(tpe: TypeRepr): TypeRepr = {
    tpe match {
      case MethodType(_, _, t) =>
        t
      case t => t
    }
  }

//  final def dropAnno(tpe: TypeRepr): TypeRepr = {
//    tpe match {
//      case AnnotatedType(t, _) =>
//        t
//      case t => t
//    }
//  }

  final def dropWrappers(tpe: TypeRepr): TypeRepr = {
    tpe match {
      case ByNameType(t) =>
        t
      case MethodType(_, _, t) =>
        t
      case t =>
        t
    }
  }

  def readWithAnno(tpe: TypeRepr): Option[TypeRepr] = {
    import izumi.distage.model.definition.With
    tpe match {
      case AnnotatedType(_, aterm) =>
        aterm.asExprOf[Any] match {
          case '{ new With[a] } =>
            Some(TypeRepr.of[a].dealias.simplified)
          case _ =>
            None
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

  def buildConstructorParameters(resultTpe: TypeRepr)(sym: Symbol): (qctx.reflect.Symbol, ParamListsRepr) = {
    val methodTypeApplied = sym.typeRef.memberType(sym.primaryConstructor).appliedTo(resultTpe.typeArgs)

    val ParamListsRepr: ParamListsRepr = {
      def go(t: TypeRepr): ParamListsRepr = {
        t match {
          case MethodType(paramNames, paramTpes, res) =>
            paramNames.zip(paramTpes) :: go(res)
          case _ =>
            Nil
        }
      }

      go(methodTypeApplied)
    }

    sym -> ParamListsRepr
  }

  def buildConstructorApplication(sym: Symbol, baseType: TypeRepr): Term = {
    Some(sym.primaryConstructor).filterNot(_.isNoSymbol) match {
      case Some(consSym) =>
        val ctorTree = Select(New(TypeIdent(sym)), consSym)
        val ctorTreeParameterized = ctorTree.appliedToTypeTrees(baseType.typeArgs.map(repr => TypeTree.of(using repr.asType)))
        ctorTreeParameterized
      case None =>
        report.errorAndAbort(s"Cannot find primary constructor in $sym")
    }
  }

  def buildParentConstructorCallTerms(
    resultTpe: TypeRepr,
    constructorParamListsRepr: List[(qctx.reflect.Symbol, ParamListsRepr)],
    contextParameters: List[Term],
  ): Seq[Term] = {
    import scala.collection.immutable.Queue
    val (_, parents) = constructorParamListsRepr.foldLeft((contextParameters, Queue.empty[Term])) {
      case ((remainingLamArgs, doneCtors), (sym, ctorParamListsRepr)) =>
        val ctorTreeParameterized = buildConstructorApplication(sym, resultTpe.baseType(sym))
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