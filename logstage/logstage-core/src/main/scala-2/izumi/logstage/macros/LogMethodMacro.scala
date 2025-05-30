package izumi.logstage.macros

import izumi.functional.quasi.{QuasiIO, QuasiPrimitives}
import izumi.fundamentals.platform.language.CodePositionMaterializer.CodePositionMaterializerMacro.getEnclosingPosition
import izumi.logstage.api.Log.Level

import scala.annotation.tailrec
import scala.reflect.macros.blackbox

final class LogMethodMacro[C <: blackbox.Context](val c: C) {
  import c.universe.*

  def exprMaybeSuspend[F[_], A](qp: c.Expr[QuasiIO[F]], expr: c.Expr[A]): c.Expr[F[A]] = {
    c.Expr[F[A]](q"$qp.maybeSuspend($expr)")
  }

  def logMethodIO[F[_], A](
    qp: c.Expr[QuasiPrimitives[F]],
    level: c.Expr[Level],
    logTypes: Boolean,
    logImplicits: Boolean,
    functionTreeToInspect: Tree,
  )(functionToUse: c.Expr[F[A]]
  ): c.Expr[F[A]] = {
    val (variables, logString) = createVariablesAndLogStringTrees(functionTreeToInspect, logTypes, logImplicits)

    val logTree =
      q"""
         $qp.tapBothUntyped($functionToUse)(
           err = error => self.log($level)(_root_.izumi.logstage.api.Log.Message.apply($logString + " => " + error))(position),
           succ = result => self.log($level)(_root_.izumi.logstage.api.Log.Message.apply($logString + " => " + result))(position)
         )   
        """

    c.Expr[F[A]](q"""
           val self = ${c.prefix}
           val position = ${getEnclosingPosition(c)}
           ..$variables
           $logTree
         """)
  }

  def logMethod[A](level: c.Expr[Level], function: c.Expr[A], logTypes: Boolean, logImplicits: Boolean): c.Expr[A] = {
    val (variables, logString) = createVariablesAndLogStringTrees(function.tree, logTypes, logImplicits)

    c.Expr[A](q"""
           val self = ${c.prefix}
           val position = ${getEnclosingPosition(c)}
           ..$variables
           try {
             val result = $function
             if (self.acceptable(position.get, $level)) {
               self.unsafeLog(_root_.izumi.logstage.api.Log.Entry.create($level, _root_.izumi.logstage.api.Log.Message.apply($logString + " => " + result))(position))
             }
             result
           } catch {
             case error: _root_.java.lang.Throwable =>
              if (self.acceptable(position.get, $level)) {
                self.unsafeLog(_root_.izumi.logstage.api.Log.Entry.create($level, _root_.izumi.logstage.api.Log.Message.apply($logString + " => " + error))(position))
              }
              throw error
           }
         """)
  }

  private def createVariablesAndLogStringTrees(function: Tree, logTypes: Boolean, logImplicits: Boolean): (List[Tree], Tree) = {
    val method = getMethodSymbols(function)
    val argumentsTreesUnordered = getFunctionArguments(function)

    val argumentsTrees =
      if (method.paramLists.size == 1) argumentsTreesUnordered
      else argumentsTreesUnordered.reverse

    val (argumentsToLog, argumentsTreesToLog) = getArgumentsToLog(argumentsTrees, method, logImplicits)

    val variables = createVariablesTrees(argumentsToLog.flatten, argumentsTreesToLog)

    val withFunctionName = q""" "Call to " + ${method.name.decodedName.toString}"""
    val withTypes = appendTypesInfo(withFunctionName, function, method, logTypes)
    val withArguments = addTermsToString(argumentsToLog, withTypes)
    (variables, withArguments)
  }

  private def appendTypesInfo(messageStringTree: Tree, funcTree: Tree, methodSymbol: MethodSymbol, logTypes: Boolean): Tree = {
    if (logTypes) {
      val typeArguments = methodSymbol.typeParams.map(_.name)
      if (typeArguments.nonEmpty) {
        val typesPassed = getFunctionTypeArguments(funcTree)
        val typeInfo = typeArguments
          .zip(typesPassed)
          .map { case (name, tpe) => s"$name=$tpe" }.mkString("[", " ", "]")
        q""" $messageStringTree + $typeInfo"""
      } else messageStringTree
    } else messageStringTree
  }

  private def getArgumentsToLog(argumentsTrees: List[Tree], methodSymbol: MethodSymbol, logImplicits: Boolean): (List[List[TermName]], List[Tree]) = {
    val methodArguments = methodSymbol.paramLists
    val (implicitArguments, nonImplicit) = methodArguments.partition(_.exists(_.isImplicit))
    val argumentsToLog =
      if (logImplicits) (nonImplicit ++ implicitArguments).map(_.map(_.name.toTermName))
      else nonImplicit.map(_.map(_.name.toTermName))

    val argumentsTreesToLog =
      if (logImplicits) argumentsTrees
      else argumentsTrees.dropRight(implicitArguments.size)

    (argumentsToLog, argumentsTreesToLog)
  }

  private def createVariablesTrees(argumentsNames: List[TermName], args: List[Tree]): List[Tree] = {
    argumentsNames
      .zip(args)
      .map { case (name, arg) => q"val $name = $arg" }
  }

  private def addTermsToString(valsNames: List[List[TermName]], stringTree: Tree): Tree = {
    @tailrec
    def loopOverArgs(args: List[TermName], acc: Tree): Tree = {
      args match {
        case Nil => acc
        case head :: Nil => q""" $acc + $head """
        case head :: tail => loopOverArgs(tail, q""" $acc + $head + ", "  """)
      }
    }

    @tailrec
    def loopOverCurriedArgs(curriedArgs: List[List[TermName]], acc: Tree): Tree = {
      curriedArgs match {
        case Nil => acc
        case head :: tail =>
          val openedBracket = q""" $acc + "("  """
          val withArgs = loopOverArgs(head, openedBracket)
          val withClosedBracket = q""" $withArgs + ")"  """
          loopOverCurriedArgs(tail, withClosedBracket)
      }
    }

    loopOverCurriedArgs(valsNames, stringTree)
  }

  private def getFunctionTypeArguments(funcTree: Tree): List[Type] = {
    @tailrec
    def loop(tree: Tree): List[Type] = tree match {
      case TypeApply(_, targs) => targs.map(_.tpe)
      case Apply(fun, _) => loop(fun)
    }
    loop(funcTree)
  }

  private def getMethodSymbols(function: Tree): MethodSymbol = {
    def getMethodBySignature(methodSignature: Type, obj: Tree, methodName: Name): MethodSymbol = {
      obj.tpe
        .member(methodName.decodedName).asTerm.alternatives
        .map(_.asMethod)
        .find(_.typeSignature == methodSignature)
        .getOrElse(c.abort(c.enclosingPosition, s"Object ${obj.symbol.name} doesn't have method $methodName with signature $methodSignature"))
    }

    @tailrec
    def loop(tree: Tree): MethodSymbol = tree match {
      case Apply(m @ Select(obj, method), _) => getMethodBySignature(m.symbol.typeSignature, obj, method)
      case Apply(TypeApply(m @ Select(obj, method), _), _) => getMethodBySignature(m.symbol.typeSignature, obj, method)

      case Apply(inner, _) => loop(inner)
      case Apply(TypeApply(inner, _), _) => loop(inner)

      case _ => c.abort(c.enclosingPosition, "Expected method or object method call")
    }
    loop(function)
  }

  private def getFunctionArguments(funcTree: Tree): List[Tree] = {
    @tailrec
    def loop(tree: Tree, acc: List[Tree]): List[Tree] = tree match {
      case Apply(Select(_, _) | TypeApply(Select(_, _), _), args) => acc ++ args

      case Apply(inner, args) => loop(inner, acc ++ args)
      case Apply(TypeApply(inner, _), args) => loop(inner, acc ++ args)

      case _ => c.abort(c.enclosingPosition, "Expected method or object method call")
    }
    loop(funcTree, List.empty[Tree])
  }
}
