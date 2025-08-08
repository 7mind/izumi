package izumi.logstage.macros

import izumi.functional.quasi.{QuasiIO, QuasiPrimitives}
import izumi.fundamentals.platform.language.CodePositionMaterializer.CodePositionMaterializerMacro
import izumi.logstage.api.Log
import izumi.logstage.api.Log.{Level, Message, StrictMessage}
import izumi.logstage.api.logger.{AbstractLogIO, AbstractLogger}

import scala.annotation.tailrec
import scala.quoted.*

object LogMethodMacro {

  def logMethodIO[A: Type, F[_]: Type, G[x] >: F[x]: Type, EncMode: Type](
    level: Expr[Level],
    function: Expr[A],
    logger: Expr[AbstractLogIO[F]],
    printTypes: Expr[Boolean],
    printImplicits: Expr[Boolean],
    qp: Expr[QuasiIO[G]],
  )(using Quotes
  ): Expr[G[A]] = {
    logMethodIOF[A, F, G, EncMode](level, '{ ${ qp }.maybeSuspend(${ function }) }, function, logger, printTypes, printImplicits, qp)
  }

  def logMethodIOF[A: Type, F[_]: Type, G[x] >: F[x]: Type, EncMode: Type](
    level: Expr[Level],
    function: Expr[G[A]],
    functionTreeToInspect: Expr[Any],
    logger: Expr[AbstractLogIO[F]],
    printTypes: Expr[Boolean],
    printImplicits: Expr[Boolean],
    qp: Expr[QuasiPrimitives[G]],
  )(using qctx: Quotes
  ): Expr[G[A]] = {
    import qctx.reflect.*
    val mode = EncodingModeExtractors.getModeFromType[EncMode]
    val (variables, fnMessage, argsMessage, typesMessage, implicitsMessage) = createVariablesAndLogMessage(mode, functionTreeToInspect.asTerm)

    '{
      val position = ${ CodePositionMaterializerMacro.getCodePositionMaterializer() }
      ${ qp }.tapBothUntyped(${ function })(
        err = error =>
          ${ logger }.log(${ level }) {
            ${
              blockWithVariables(qctx)(variables) {
                '{
                  val typesMsg = ${ ifOrEmptyMsg(printTypes)(typesMessage) }
                  val implicitsMsg = ${ ifOrEmptyMsg(printImplicits)(implicitsMessage) }
                  val errorMsg = error match {
                    case error: Throwable => ${ messageMacro(mode, '{ " => " + error }) }
                    case error => ${ messageMacro(mode, '{ " => " + error }) }
                  }
                  ${ fnMessage } ++ typesMsg ++ ${ argsMessage } ++ implicitsMsg ++ errorMsg
                }
              }
            }
          }(using position),
        succ = result =>
          ${ logger }.log(${ level }) {
            ${
              blockWithVariables(qctx)(variables) {
                '{
                  val typesMsg = ${ ifOrEmptyMsg(printTypes)(typesMessage) }
                  val implicitsMsg = ${ ifOrEmptyMsg(printImplicits)(implicitsMessage) }
                  ${ fnMessage } ++ typesMsg ++ ${ argsMessage } ++ implicitsMsg ++ ${ messageMacro(mode, '{ " => " + result }) }
                }
              }
            }
          }(using position),
      )
    }
  }

  def logMethod[A: Type, EncMode: Type](
    level: Expr[Level],
    function: Expr[A],
    logger: Expr[AbstractLogger],
    printTypes: Expr[Boolean],
    printImplicits: Expr[Boolean],
  )(using qctx: Quotes
  ): Expr[A] = {
    import qctx.reflect.*
    val mode = EncodingModeExtractors.getModeFromType[EncMode]
    val (variables, fnMessage, argsMessage, typesMessage, implicitsMessage) = createVariablesAndLogMessage(mode, function.asTerm)

    '{
      val position = ${ CodePositionMaterializerMacro.getCodePositionMaterializer() }
      try {
        val result = ${ function }
        if (${ logger }.acceptable(position.get, ${ level })) {
          ${
            blockWithVariables(qctx)(variables) {
              '{
                val typesMsg = ${ ifOrEmptyMsg(printTypes)(typesMessage) }
                val implicitsMsg = ${ ifOrEmptyMsg(printImplicits)(implicitsMessage) }
                ${ logger }.unsafeLog(
                  Log.Entry.create(
                    ${ level },
                    ${ fnMessage } ++ typesMsg ++ ${ argsMessage } ++ implicitsMsg ++ ${ messageMacro(mode, '{ " => " + result }) },
                  )(using position)
                )
              }
            }
          }
        }
        result
      } catch {
        case error: Throwable =>
          if (${ logger }.acceptable(position.get, ${ level })) {
            ${
              blockWithVariables(qctx)(variables) {
                '{
                  val typesMsg = ${ ifOrEmptyMsg(printTypes)(typesMessage) }
                  val implicitsMsg = ${ ifOrEmptyMsg(printImplicits)(implicitsMessage) }
                  ${ logger }.unsafeLog(
                    Log.Entry.create(
                      ${ level },
                      ${ fnMessage } ++ typesMsg ++ ${ argsMessage } ++ implicitsMsg ++ ${ messageMacro(mode, '{ " => " + error }) },
                    )(using position)
                  )
                }
              }
            }
          }
          throw error
      }
    }
  }

  private def blockWithVariables[A: Type](qctx: Quotes)(variables: List[qctx.reflect.ValDef])(expr: Expr[A]): Expr[A] = {
    import qctx.reflect.{Block, asTerm}
    Block(variables, expr.asTerm).asExprOf[A]
  }

  private def createVariablesAndLogMessage(
    using qctx: Quotes
  )(mode: EncodingMode,
    funcTree: qctx.reflect.Term,
  ): (List[qctx.reflect.ValDef], Expr[Message], Expr[Message], Expr[Message], Expr[Message]) = {
    import qctx.reflect.*
    val (method, argumentsTreess) = getFunctionArgumentsAndMethodSymbol(funcTree)

    val (methodTypeArguments, methodArguments) = method.paramSymss.partition(_.exists(_.isType))

    val (explicitVariableDecls, implicitVariableDecls) = getArgumentsToLog(methodArguments, argumentsTreess)

    val fnMessage = '{ Message.raw(${ Expr(s"Call to ${method.name}") }) }
    val (typeVariableDecls, typesMessage) = mkTypesMsg(mode, funcTree, methodTypeArguments.flatten)
    val variableValDefs: List[ValDef] = ((explicitVariableDecls.iterator ++ implicitVariableDecls).flatten ++ typeVariableDecls).map(_._1).toList
    val argMessage = messageMacro(mode, mkParametersString(explicitVariableDecls.map(_.map(_._2)), Expr(""), "(", ")"))
    val implicitsMessage = messageMacro(mode, mkParametersString(implicitVariableDecls.map(_.map(_._2)), Expr(""), "(using ", ")"))

    (variableValDefs, fnMessage, argMessage, typesMessage, implicitsMessage)
  }

  private def mkTypesMsg(
    using qctx: Quotes
  )(mode: EncodingMode,
    funcTree: qctx.reflect.Term,
    typeArguments: List[qctx.reflect.Symbol],
  ): (List[(qctx.reflect.ValDef, Expr[Any])], Expr[Message]) = {
    import qctx.reflect.*
    if (typeArguments.nonEmpty) {
      val typesPassed = getFunctionTypeArguments(funcTree)
      val typeVariableNames = typeArguments.map(_.name)
      val typeVariableValues = typesPassed.map(t => Literal(StringConstant(t.show(using Printer.TypeReprShortCode))))
      val typeVariableDecls = createVariableTrees(using qctx)(identity[String]) {
        typeVariableNames.zip(typeVariableValues)
      }
      val stringTree = mkParametersString(List(typeVariableDecls.map(_._2)), Expr(""), "[", "]")
      (typeVariableDecls, messageMacro(mode, stringTree))
    } else {
      (Nil, emptyMessageTree)
    }
  }

  private def messageMacro(mode: EncodingMode, stringExpr: Expr[String])(using Quotes): Expr[Message] = {
    mode match {
      case EncodingMode.NonStrict => '{ Message.apply(${ stringExpr }) }
      case EncodingMode.Strict => '{ StrictMessage.apply(${ stringExpr }) }
      case EncodingMode.Raw => '{ Message.raw(${ stringExpr }) }
    }
  }

  private def emptyMessageTree(using Quotes): Expr[Message] = {
    '{ Message.empty }
  }

  private def ifOrEmptyMsg(bool: Expr[Boolean])(message: Expr[Message])(using Quotes): Expr[Message] = {
    '{
      if (${ bool }) ${ message }
      else ${ emptyMessageTree }
    }
  }

  private def getArgumentsToLog(
    using qctx: Quotes
  )(methodArgumentss: List[List[qctx.reflect.Symbol]],
    argumentsTreess: List[List[qctx.reflect.Term]],
  ): (List[List[(qctx.reflect.ValDef, Expr[Any])]], List[List[(qctx.reflect.ValDef, Expr[Any])]]) = {
    import qctx.reflect.*

    def isImplicit(symbol: Symbol): Boolean = symbol.flags.is(Flags.Given) || symbol.flags.is(Flags.Implicit)

    val zipped = methodArgumentss.zip(argumentsTreess).map((as, ts) => as.zip(ts))
    val (implicits, explicits) = zipped.partition(_.exists((s, _) => isImplicit(s)))

    val explicitArgumentss = explicits.map(createVariableTrees(_.name))
    val implicitArgumentss = implicits.map(createVariableTrees(_.name))

    (explicitArgumentss, implicitArgumentss)
  }

  private def createVariableTrees[A](using qctx: Quotes)(getName: A => String)(namesTerms: List[(A, qctx.reflect.Term)]): List[(qctx.reflect.ValDef, Expr[Any])] = {
    import qctx.reflect.*
    namesTerms.map {
      (a, term) =>
        val sym = Symbol.newVal(Symbol.spliceOwner, getName(a), term.tpe.widen, Flags.Lazy, Symbol.noSymbol)
        (ValDef(sym, Some(term)), Ref(sym).asExpr)
    }
  }

  private def mkParametersString(
    using Quotes
  )(valExprss: List[List[Expr[Any]]],
    stringTree: Expr[String],
    bracketOpen: String,
    bracketClose: String,
  ): Expr[String] = {
    val bOpenExpr = Expr(bracketOpen)
    val bCloseExpr = Expr(bracketClose)
    valExprss.foldLeft(stringTree) {
      (acc, valExprs) =>
        val openedBracket: Expr[String] = '{ ${ acc } + ${ bOpenExpr } }
        val withArgs = valExprs match {
          case Nil => openedBracket
          case head :: tail =>
            tail.foldLeft('{ ${ openedBracket } + ${ head } })((a, b) => '{ ${ a } + ", " + ${ b } })
        }
        '{ ${ withArgs } + ${ bCloseExpr } }
    }
  }

  private def getFunctionTypeArguments(using qctx: Quotes)(funcTree: qctx.reflect.Tree): List[qctx.reflect.TypeRepr] = {
    import qctx.reflect.*
    @tailrec
    def loop(tree: Tree): List[TypeRepr] = tree match {
      case TypeApply(_, targs) => targs.map(_.tpe)
      case Apply(tree, _) => loop(tree)
      case Inlined(_, _, tree) => loop(tree)
      case Block(List(), tree) => loop(tree)
      case _ => Nil
    }
    loop(funcTree)
  }

  private def getFunctionArgumentsAndMethodSymbol(using qctx: Quotes)(funcTree: qctx.reflect.Term): (qctx.reflect.Symbol, List[List[qctx.reflect.Term]]) = {
    import qctx.reflect.*
    @tailrec
    def loop(tree: Term, argss: List[List[Term]]): (Symbol, List[List[Term]]) = tree match {
      case Apply(m @ Select(_, _), args) => (m.symbol, args :: argss)
      case Apply(m @ TypeApply(Select(_, _), _), args) => (m.symbol, args :: argss)

      case Inlined(_, _, term) => loop(term, argss)
      case Block(List(), term) => loop(term, argss)
      case Apply(TypeApply(term, _), args) => loop(term, args :: argss)
      case Apply(term, args) => loop(term, args :: argss)

      case _ => report.errorAndAbort(s"Expected method call, but got ${tree.show} (raw=$tree)")
    }
    loop(funcTree, Nil)
  }
}
