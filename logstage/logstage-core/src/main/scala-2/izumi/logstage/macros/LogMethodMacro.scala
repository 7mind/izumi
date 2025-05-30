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
    val method = getMethodSymbol(function)
    val argumentsTreesUnordered = getFunctionArguments(function)

    val argumentsTrees =
      if (method.paramLists.size == 1) argumentsTreesUnordered
      else argumentsTreesUnordered.reverse

    val (termVariableNamess, termVariableValues) = getArgumentsToLog(argumentsTrees, method, logImplicits)

    val termVariableDecls = createVariablesTrees(termVariableNamess.flatten, termVariableValues)

    val withFunctionName = q"${s"Call to ${method.name.decodedName.toString}"}"
    val (typeVariableDecls, withTypes) = appendTypesInfo(withFunctionName, function, method, logTypes)
    val withArguments = addTermsToString(termVariableNamess, withTypes, "(", ")")
    (termVariableDecls ++ typeVariableDecls, withArguments)
  }

  private def appendTypesInfo(messageStringTree: Tree, funcTree: Tree, methodSymbol: MethodSymbol, logTypes: Boolean): (List[Tree], Tree) = {
    if (logTypes) {
      val typeArguments = methodSymbol.typeParams.map(_.name)
      if (typeArguments.nonEmpty) {
        val typesPassed = getFunctionTypeArguments(funcTree)
        val typeVariableNames = typeArguments.map(_.toTermName)
        val typeVariableValues = typesPassed.map(t => q"${show(t)}")
        val typeVariableDecls = createVariablesTrees(typeVariableNames, typeVariableValues)
        (typeVariableDecls, addTermsToString(List(typeVariableNames), messageStringTree, "[", "]"))
      } else {
        (Nil, messageStringTree)
      }
    } else {
      (Nil, messageStringTree)
    }
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
    argumentsNames.iterator.zip(args).map { case (name, arg) => q"val $name = $arg" }.toList
  }

  private def addTermsToString(valsNamess: List[List[TermName]], stringTree: Tree, bracketOpen: String, bracketClose: String): Tree = {
    valsNamess.foldLeft(stringTree) {
      (acc, valNames) =>
        val openedBracket = q""" $acc + $bracketOpen """
        val withArgs = valNames match {
          case Nil => openedBracket
          case head :: tail => tail.foldLeft[Tree](q"$openedBracket + $head")((a, b) => q""" $a + ", " + $b """)
        }
        q""" $withArgs + $bracketClose """
    }
  }

  private def getFunctionTypeArguments(funcTree: Tree): List[Type] = {
    @tailrec
    def loop(tree: Tree): List[Type] = tree match {
      case TypeApply(_, targs) => targs.map(_.tpe)
      case Apply(fun, _) => loop(fun)
      case _ => Nil
    }
    loop(funcTree)
  }

  private def getMethodSymbol(function: Tree): MethodSymbol = {
    @tailrec
    def loop(tree: Tree): MethodSymbol = tree match {
      case Apply(m: SelectApi, _) if m.symbol.isMethod => m.symbol.asMethod
      case Apply(TypeApply(m: SelectApi, _), _) if m.symbol.isMethod => m.symbol.asMethod

      case TypeApply(inner, _) => loop(inner)
      case Apply(inner, _) => loop(inner)

      case _ => c.abort(c.enclosingPosition, s"Expected method call, but got ${showCode(tree)} (raw=${showRaw(tree)})")
    }
    loop(function)
  }

  private def getFunctionArguments(funcTree: Tree): List[Tree] = {
    @tailrec
    def loop(tree: Tree, acc: List[Tree]): List[Tree] = tree match {
      case Apply(Select(_, _) | TypeApply(Select(_, _), _), args) => acc ++ args

      case Apply(inner, args) => loop(inner, acc ++ args)
      case Apply(TypeApply(inner, _), args) => loop(inner, acc ++ args)

      case _ => c.abort(c.enclosingPosition, s"Expected method call, but got ${showCode(tree)} (raw=${showRaw(tree)})")
    }
    loop(funcTree, List.empty[Tree])
  }
}
