package izumi.distage.constructors

import izumi.fundamentals.platform.reflection.ReflectionUtil
import scala.quoted.{Expr, Quotes, Type}
import scala.collection.mutable

class ConstructorUtil[Q <: Quotes](using val qctx: Q) {
  import qctx.reflect.*

  type ParamListRepr = List[(String, qctx.reflect.TypeRepr)]
  type ParamListsRepr = List[ParamListRepr]
  type ParamListTree = List[(String, qctx.reflect.TypeTree)]
  type ParamListsTree = List[ParamListTree]

  implicit class ParamListExt(params: ParamListRepr) {
    def toTrees: ParamListTree = params.map((n, t) => (n, TypeTree.of(using t.asType))).toList
  }

  def requireConcreteTypeConstructor[R: Type](macroName: String): Unit = {
    val tpe = TypeRepr.of[R]
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
      _ => params.map(_._2.tpe match { case t @ ByNameType(_) => t; case t => ByNameType(t) }),
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
    val argTypes = extractArgs(resultTpe.baseType(sym))
    val methodTypeApplied = sym.typeRef.memberType(sym.primaryConstructor).appliedTo(argTypes)

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

  def extractArgs(baseType: TypeRepr): List[TypeRepr] = {
    baseType match {
      case AppliedType(_, args) =>
        args
      case _ =>
        Nil
    }
  }

  def buildConstructorApplication(sym: Symbol, baseType: TypeRepr): Term = {
    Some(sym.primaryConstructor).filterNot(_.isNoSymbol) match {
      case Some(consSym) =>
        val ctorTree = Select(New(TypeIdent(sym)), consSym)
        val argTypes = extractArgs(baseType).map(repr => TypeTree.of(using repr.asType))
        val ctorTreeParameterized = ctorTree.appliedToTypeTrees(argTypes)
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
