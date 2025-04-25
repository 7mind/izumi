package izumi.logstage.api

import izumi.functional.quasi.{QuasiIO, QuasiPrimitives}
import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.logstage.api.Log.{Level, Message}
import izumi.logstage.api.logger.{AbstractLogIO, AbstractLogger}

import scala.annotation.tailrec
import scala.quoted.*

object LogMethodMacro {
  def logMethodIOF[A: Type, B, F[_]: Type, G[x] >: F[x]: Type](
    using qctx: Quotes
  )(level: Expr[Level],
    function: Expr[G[A]],
    functionTreeToInspect: Expr[B],
    logger: Expr[AbstractLogIO[F]],
    logTypesExpr: Expr[Boolean],
    logImplicitsExpr: Expr[Boolean],
    qp: Expr[QuasiPrimitives[G]],
  ): Expr[G[A]] = {
    import qctx.reflect.*
    val (variables, logMessage) = createVariablesAndLogMessage(functionTreeToInspect.asTerm, logTypesExpr, logImplicitsExpr)

    val logExpr =
      '{
        $qp.tapBothUntyped($function)(
          err = error => $logger.log($level)(Message($logMessage + " => " + error))(CodePositionMaterializer.materialize),
          succ = result => $logger.log($level)(Message($logMessage + " => " + result))(CodePositionMaterializer.materialize),
        )
      }.asTerm

    Block(
      variables,
      logExpr,
    ).asExprOf[G[A]]
  }

  def logMethodIO[A: Type, F[_]: Type, G[x] >: F[x]: Type](
    level: Expr[Level],
    function: Expr[A],
    logger: Expr[AbstractLogIO[F]],
    logTypesExpr: Expr[Boolean],
    logImplicitsExpr: Expr[Boolean],
    qp: Expr[QuasiIO[G]],
  )(using qctx: Quotes
  ): Expr[G[A]] = {
    logMethodIOF(level, '{ $qp.maybeSuspend($function) }, function, logger, logTypesExpr, logImplicitsExpr, qp)
  }

  def logMethod[A: Type](
    level: Expr[Level],
    function: Expr[A],
    logger: Expr[AbstractLogger],
    logTypesExpr: Expr[Boolean],
    logImplicitsExpr: Expr[Boolean],
  )(using qctx: Quotes
  ): Expr[A] = {
    import qctx.reflect.*
    val funcTree = function.asTerm
    val (variables, logMessage) = createVariablesAndLogMessage(funcTree, logTypesExpr, logImplicitsExpr)

    val logExpr = '{
      val pos = CodePositionMaterializer.materialize
      try {
        val result = $function
        if ($logger.acceptable(pos.get, $level)) {
          $logger.unsafeLog(Log.Entry.create($level, Message($logMessage + " => " + result))(pos))
        }
        result
      } catch {
        case error: Throwable =>
          if ($logger.acceptable(pos.get, $level)) {
            $logger.unsafeLog(Log.Entry.create($level, Message($logMessage + " => " + error))(pos))
          }
          throw error
      }
    }.asTerm

    Block(
      variables,
      logExpr,
    ).asExprOf[A]
  }

  private def createVariablesAndLogMessage(
    using qctx: Quotes
  )(funcTree: qctx.reflect.Term,
    logTypesExpr: Expr[Boolean],
    logImplicitsExpr: Expr[Boolean],
  ): (List[qctx.reflect.ValDef], Expr[String]) = {
    import qctx.reflect.*
    val logTypes: Boolean = logTypesExpr.value match {
      case Some(value) => value
      case None => true
    }
    val logImplicits = logImplicitsExpr.value match {
      case Some(value) => value
      case None => true
    }

    val methodsSymbols = getMethodSymbols(funcTree)
    val argumentsTreesUnordered = getMethodArguments(funcTree)
    
    val method =
      if (methodsSymbols.size == 1) {
        methodsSymbols.head
      } else {
        methodsSymbols.find(_.paramSymss.flatten.size == argumentsTreesUnordered.size).head
      }
      
    val argumentsTrees =
      if (method.paramSymss.size == 1) argumentsTreesUnordered
      else argumentsTreesUnordered.reverse
    
    val methodParams = method.paramSymss
    val (methodTypeArguments, methodArguments) = methodParams.partition(_.exists(_.isType))
    val variablesSymbols = createVariablesSymbols(methodArguments, argumentsTrees, logImplicits)
    val variables: List[ValDef] = variablesSymbols.flatten.zip(argumentsTrees).map {
      case (symbol, tree) => ValDef(symbol, Some(tree))
    }
    val withFunctionName = Expr(s"Call to ${method.name}")
    val withTypes = appendTypesInfo(funcTree, methodTypeArguments.flatten, withFunctionName, logTypes)
    val withArguments = appendSymbolsToString(variablesSymbols, withTypes)
    (variables, withArguments)
  }

  private def appendTypesInfo(
    using qctx: Quotes
  )(funcTree: qctx.reflect.Term,
    methodTypeArguments: List[qctx.reflect.Symbol],
    message: Expr[String],
    logTypes: Boolean,
  ) = {
    if (logTypes && methodTypeArguments.nonEmpty) {
      val typesPassed = getFunctionTypeArguments(funcTree)
      val typeInfo = methodTypeArguments
        .zip(typesPassed)
        .map { case (typeArgument, typeTree) => s"${typeArgument.name}=${typeTree.show}" }.mkString("[", " ", "]")
      '{ $message + ${ Expr[String](typeInfo) } }
    } else message
  }

  private def createVariablesSymbols(
    using qctx: Quotes
  )(args: List[List[qctx.reflect.Symbol]],
    argsTrees: List[qctx.reflect.Term],
    logImplicits: Boolean,
  ): List[List[qctx.reflect.Symbol]] = {
    import qctx.reflect.*

    def isImplicit(symbol: Symbol): Boolean = symbol.flags.is(Flags.Given) || symbol.flags.is(Flags.Implicit)

    @tailrec
    def loopOverArgs(symbols: List[Symbol], argsTrees: IndexedSeq[Term], index: Int, acc: List[Symbol]): (List[Symbol], Int) = {
      symbols match {
        case Nil => (acc, index)
        case head :: tail =>
          if (!logImplicits && isImplicit(head)) {
            loopOverArgs(tail, argsTrees, index + 1, acc)
          } else {
            val valSymbol = Symbol.newVal(Symbol.spliceOwner, head.name, argsTrees(index).tpe.widen, Flags.EmptyFlags, Symbol.noSymbol)
            loopOverArgs(tail, argsTrees, index + 1, acc :+ valSymbol)
          }
      }
    }

    @tailrec
    def loopOverCurriedArgs(args: List[List[Symbol]], argsTrees: IndexedSeq[Term], index: Int, acc: List[List[Symbol]]): List[List[Symbol]] = {
      args match {
        case Nil => acc
        case head :: tail =>
          val (symbols, newIndex) = loopOverArgs(head, argsTrees, index, Nil)
          loopOverCurriedArgs(tail, argsTrees, newIndex, acc :+ symbols)
      }
    }

    loopOverCurriedArgs(args, argsTrees.toIndexedSeq, 0, Nil).filter(_.nonEmpty)
  }

  private def appendSymbolsToString(
    using qctx: Quotes
  )(symbols: List[List[qctx.reflect.Symbol]],
    stringTree: Expr[String],
  ): Expr[String] = {
    import qctx.reflect.*
    @tailrec
    def loopOverArgs(args: List[Symbol], acc: Expr[String]): Expr[String] = {
      args match {
        case Nil => acc
        case head :: Nil => '{ $acc + ${ Ref(head).asExpr } }
        case head :: tail => loopOverArgs(tail, '{ $acc + ${ Ref(head).asExpr } + ", " })
      }
    }

    @tailrec
    def loopOverCurriedArgs(curriedArgs: List[List[Symbol]], acc: Expr[String]): Expr[String] = {
      curriedArgs match {
        case Nil => acc
        case head :: tail =>
          val openedBracket = '{ $acc + "(" }
          val withArgs = loopOverArgs(head, openedBracket)
          val withClosedBracket = '{ $withArgs + ")" }
          loopOverCurriedArgs(tail, withClosedBracket)
      }
    }

    if (symbols.isEmpty) '{ $stringTree + "()" }
    else loopOverCurriedArgs(symbols, stringTree)
  }

  private def getFunctionTypeArguments(using qctx: Quotes)(funcTree: qctx.reflect.Tree): List[qctx.reflect.TypeRepr] = {
    import qctx.reflect.*
    @tailrec
    def loop(tree: Tree): List[TypeTree] = tree match {
      case TypeApply(_, targs) => targs
      case Apply(tree, _) => loop(tree)
      case Inlined(_, _, tree) => loop(tree)
    }

    loop(funcTree).map(_.tpe)
  }

  private def getMethodSymbols(using qctx: Quotes)(function: qctx.reflect.Term): List[qctx.reflect.Symbol] = {
    import qctx.reflect.*
    @tailrec
    def loop(tree: Term): List[Symbol] = tree match {
      case Apply(Select(obj, method), _) => obj.symbol.methodMember(method)
      case Apply(TypeApply(Select(obj, method), _), _) => obj.symbol.methodMember(method)

      case Inlined(_, _, term) => loop(term)
      case Apply(TypeApply(term, _), _) => loop(term)
      case Apply(term, _) => loop(term)

      case _ => report.errorAndAbort("The expression must be class or object method call")
    }

    loop(function)
  }

  private def getMethodArguments(using qctx: Quotes)(function: qctx.reflect.Term): List[qctx.reflect.Term] = {
    import qctx.reflect.*
    @tailrec
    def loop(tree: Term, acc: List[Term]): List[Term] = tree match {
      case Apply(Select(_, _) | TypeApply(Select(_, _), _), args) => acc ++ args

      case Inlined(_, _, term) => loop(term, acc)
      case Apply(TypeApply(term, _), args) => loop(term, acc ++ args)
      case Apply(term, args) => loop(term, acc ++ args)

      case _ => report.errorAndAbort("The expression must be class or object method call")
    }

    loop(function, List.empty[Term])
  }
}
