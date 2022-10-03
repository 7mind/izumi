package izumi.distage.constructors

import izumi.distage.model.providers.{Functoid, FunctoidMacro}
import izumi.fundamentals.platform.exceptions.IzThrowable.toRichThrowable

import scala.annotation.experimental
import scala.collection.immutable.{ArraySeq, Queue}
import scala.collection.mutable
import scala.quoted.{Expr, Quotes, Type}

object TraitConstructorMacro {

  @experimental
  def make[R: Type](using qctx: Quotes): Expr[TraitConstructor[R]] = try {
    import qctx.reflect.*

    val functoidMacro = new FunctoidMacro.FunctoidMacroImpl[qctx.type]()

    val resultTpe = TypeRepr.of[R].dealias.simplified
    val resultTypeTree = TypeTree.of[R]
    val resultTpeSym = resultTpe.typeSymbol

    val abstractMethods = resultTpeSym.methodMembers
      .filter(m => m.flags.is(Flags.Method) && m.flags.is(Flags.Deferred) && !m.flags.is(Flags.Artifact) && m.isDefDef)

    val abstractMethodsWithParams = abstractMethods.filter(_.paramSymss.nonEmpty)

    if (abstractMethodsWithParams.nonEmpty) {
      report.errorAndAbort(
        s"""$resultTpeSym has abstract methods taking parameters, expected only parameterless abstract methods:
           |  ${abstractMethodsWithParams.map(s => s.name -> s.flags.show)}
           |  [others: ${abstractMethods.map(s => s.name -> s.flags.show)}]""".stripMargin
      )
    }

    val parentsSymbols = if (!resultTpeSym.flags.is(Flags.Trait)) {
      List(resultTpeSym)
    } else {
      val seen = mutable.HashSet[Symbol](defn.ObjectClass, defn.MatchableClass, defn.AnyRefClass, defn.AnyValClass, defn.AnyClass)
      val banned = mutable.HashSet[Symbol](defn.ObjectClass, defn.MatchableClass, defn.AnyRefClass, defn.AnyValClass, defn.AnyClass)
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

    val methodDecls = abstractMethods.map(m => (m.name, m.owner.typeRef.memberType(m)))

    def decls(cls: Symbol): List[Symbol] = methodDecls.map {
      case (name, mtype) =>
        // for () methods: MethodType(Nil)(_ => Nil, _ => m.returnTpt.symbol.typeRef)
        Symbol.newMethod(cls, name, mtype, Flags.Method | Flags.Override, Symbol.noSymbol)
    }

    val constructorParamLists = parentsSymbols.map {
      sym =>
        // TODO: decopypaste

        val argTypes = resultTpe.baseType(sym) match {
          case AppliedType(_, args) =>
            args
          case _ =>
            Nil
        }

        val methodTypeApplied = sym.typeRef.memberType(sym.primaryConstructor).appliedTo(argTypes)

        val paramLists: List[List[(String, TypeRepr)]] = {
          def go(t: TypeRepr): List[List[(String, TypeRepr)]] = {
            t match {
              case MethodType(paramNames, paramTpes, res) =>
                paramNames.zip(paramTpes) :: go(res)
              case _ =>
                Nil
            }
          }
          go(methodTypeApplied)
        }

        sym -> paramLists
    }

    val flatCtorParams = constructorParamLists.iterator.flatMap(_._2.iterator.flatten).to(ArraySeq)
    val lamParams = {
      val byNameMethodArgs = methodDecls.iterator.map((n, t) => (s"_$n", t match { case ByNameType(_) => t; case _ => ByNameType(t) }))
      (flatCtorParams ++ byNameMethodArgs).map((n, t) => (n, TypeTree.of(using t.asType))).toList
    }

    val lamExpr = ConstructorUtil.wrapIntoLambda[qctx.type, R](List(lamParams)) {
      (lamSym, args0) =>

        val (lamOnlyCtorArguments, lamOnlyMethodArguments) = args0.splitAt(flatCtorParams.size)

        val (_, parents) = constructorParamLists.foldLeft((lamOnlyCtorArguments, Queue.empty[Term])) {
          case ((remainingLamArgs, doneCtors), (sym, ctorParamLists)) =>
            // TODO decopypaste

            val consSym = sym.primaryConstructor
            val ctorTree = Select(New(TypeIdent(sym)), consSym)

            val argTypes = resultTpe.baseType(sym) match {
              case AppliedType(_, args) =>
                args.map(repr => TypeTree.of(using repr.asType))
              case _ =>
                Nil
            }
            val ctorTreeParameterized = ctorTree.appliedToTypeTrees(argTypes)

            val (rem, argsLists) = ctorParamLists.foldLeft((remainingLamArgs, Queue.empty[List[Term]])) {
              case ((lamArgs, res), params) =>
                val (argList, rest) = lamArgs.splitAt(params.size)
                (rest, res :+ argList)
            }

            val appl = argsLists.foldLeft(ctorTreeParameterized)(_.appliedToArgs(_))
            (rem, doneCtors :+ appl)
        }

        val name: String = s"${resultTpeSym.name}AutoImpl"
        val clsSym = Symbol.newClass(lamSym, name, parents = parentsSymbols.map(_.typeRef), decls = decls, selfType = None)

        val defs = methodDecls.zip(lamOnlyMethodArguments).map {
          case ((name, _), arg) =>
            val fooSym = clsSym.declaredMethod(name).head

            DefDef(fooSym, _ => Some(arg))
        }

        val clsDef = ClassDef(clsSym, parents.toList, body = defs)
        val newCls = Typed(Apply(Select(New(TypeIdent(clsSym)), clsSym.primaryConstructor), Nil), resultTypeTree)
        val block = Block(List(clsDef), newCls)
        Typed(block, resultTypeTree)
    }

    report.warning(
      s"""|symbol = $resultTpeSym, flags=${resultTpeSym.flags.show}
          |methods = ${resultTpeSym.methodMembers.map(s => s"name: ${s.name} flags ${s.flags.show}")}
          |tree = ${resultTpeSym.tree}
          |pcs  = ${resultTpeSym.primaryConstructor.tree.show}
          |pct  = ${resultTpeSym.primaryConstructor.tree}
          |pct-flags = ${resultTpeSym.primaryConstructor.flags.show}
          |pctt = ${resultTpeSym.typeRef.memberType(resultTpeSym.primaryConstructor)}
          |pcts = ${resultTpeSym.typeRef.baseClasses
           .map(s => (s, s.primaryConstructor)).map((cs, s) => if (s != Symbol.noSymbol) (cs, cs.flags.show, s.tree) else (cs, cs.flags.show, None))
           .mkString("\n")}
          |defn = ${resultTpeSym.tree.show}
          |lam  = ${lamExpr.asTerm}
          |lam  = ${lamExpr.show}
          |""".stripMargin
    )

    val f = functoidMacro.make[R](lamExpr)
    '{ new TraitConstructor[R](${ f }) }

  } catch { case t: Throwable => qctx.reflect.report.errorAndAbort(t.stackTrace) }

}
