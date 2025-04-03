package izumi.logstage.macros

import izumi.fundamentals.platform.language.CodePositionMaterializer.CodePositionMaterializerMacro.getEnclosingPosition
import izumi.logstage.api.Log.Level

import scala.annotation.tailrec
import scala.reflect.macros.blackbox

class LogMethodMacro[C <: blackbox.Context](final val c: C) {
  import c.universe.*

  def logMethodIO[F[_], A](level: c.Expr[Level], function: c.Expr[F[A]], logTypes: Boolean, logImplicits: Boolean): c.Expr[F[A]] = {
    val (variables, logString) = createVariablesAndLogStringTrees(function, logTypes, logImplicits)

    val isWrapped = isWrappedInF(function.tree)

    val logTree =
      if (isWrapped) {
        q"""
         $function.flatMap(result =>
           self
           .log($level)(_root_.izumi.logstage.api.Log.Message.apply($logString))(position)
           .map(_ => result)
         )
        """
      } else {
        q"""
         val result = $function
         self.log($level)(_root_.izumi.logstage.api.Log.Message.apply($logString))(position).map(_ => result)
        """
      }

    c.Expr[F[A]](q"""
           val self = ${c.prefix}
           val position = ${getEnclosingPosition(c)}
           ..$variables
           $logTree
         """)
  }

  def logMethod[A](level: c.Expr[Level], function: c.Expr[A], logTypes: Boolean, logImplicits: Boolean): c.Expr[A] = {
    val (variables, logString) = createVariablesAndLogStringTrees(function, logTypes, logImplicits)

    c.Expr[A](q"""
           val self = ${c.prefix}
           val position = ${getEnclosingPosition(c)}
           ..$variables
           val result = $function
           val msg = _root_.izumi.logstage.api.Log.Message.apply($logString)
           if (self.acceptable(position.get, $level)) {
              self.unsafeLog(Log.Entry.create($level, msg)(position))
           }
           result
         """)
  }

  private def createVariablesAndLogStringTrees[A](function: c.Expr[A], logTypes: Boolean, logImplicits: Boolean): (List[Tree], Tree) = {
    val funcTree = function.tree
    val method = getMethodSymbol(funcTree)

    val (argumentsToLog, argumentsTreesToLog) = getArgumentsToLog(funcTree, method, logImplicits)

    val variables = createVariablesTrees(argumentsToLog.flatten, argumentsTreesToLog)

    val withFunctionName = q""" "Call to " + ${method.name.decodedName.toString}"""
    val withTypes = appendTypesInfo(withFunctionName, funcTree, method, logTypes)
    val withArguments = addTermsToString(argumentsToLog, withTypes)
    val withResult = q""" $withArguments + " => " + result """
    (variables, withResult)
  }

  private def isWrappedInF(funcTree: Tree): Boolean = {
    val funcTypeConstructor = funcTree.tpe.dealias.typeSymbol
    val thisType = c.prefix.tree.tpe
    val parentTypes: List[Type] = thisType.baseClasses.map(thisType.baseType)
    val parentTypeArgs =
      parentTypes.flatMap(parent => parent.typeArgs.map(_.dealias.typeSymbol))

    parentTypeArgs.contains(funcTypeConstructor)
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

  private def getArgumentsToLog(funcTree: Tree, methodSymbol: MethodSymbol, logImplicits: Boolean): (List[List[TermName]], List[Tree]) = {
    val methodArguments = methodSymbol.paramLists
    val (implicitArguments, nonImplicit) = methodArguments.partition(_.exists(_.isImplicit))
    val argumentsToLog =
      if (logImplicits) (nonImplicit ++ implicitArguments).map(_.map(_.name.toTermName))
      else nonImplicit.map(_.map(_.name.toTermName))

    val argumentsTrees = getFunctionArguments(funcTree)
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

  private def getMethodSymbol(function: Tree): MethodSymbol = {
    @tailrec
    def loop(tree: Tree): MethodSymbol = tree match {
      case Apply(Select(obj, method), _) => obj.tpe.member(method.decodedName).asMethod
      case Apply(TypeApply(Select(obj, method), _), _) => obj.tpe.member(method.decodedName).asMethod

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
    loop(funcTree, List.empty[Tree]).reverse
  }
}
