package izumi.logstage.macros

import izumi.functional.quasi.{QuasiIO, QuasiPrimitives}
import izumi.fundamentals.platform.language.CodePositionMaterializer.CodePositionMaterializerMacro
import izumi.logstage.api.Log.Level
import izumi.logstage.api.logger.{AbstractLogIO, AbstractLogger}

import scala.annotation.tailrec
import scala.reflect.macros.blackbox

final class LogMethodMacro[C <: blackbox.Context](val c: C) {
  import c.universe.*

  private val emptyMessageTree: Tree = {
    q"_root_.izumi.logstage.api.Log.Message.empty"
  }

  private def messageMacro(mode: EncodingMode, stringTree: Tree): Tree = {
    stringTree match {
      case Literal(Constant("")) =>
        emptyMessageTree
      case _ =>
        mode match {
          case EncodingMode.NonStrict =>
            q"_root_.izumi.logstage.api.Log.Message.apply($stringTree)"
          case EncodingMode.Strict =>
            q"_root_.izumi.logstage.api.Log.StrictMessage.apply($stringTree)"
          case EncodingMode.Raw =>
            q"_root_.izumi.logstage.api.Log.Message.raw($stringTree)"
        }
      // LogMessageMacro.createMessageWithMode(c)(c.Expr[String](stringTree), EncodingMode.NonStrict) // doesn't find LogstageCodec due to empty .tpes in passed untyped tree
    }
  }

  def exprMaybeSuspend[F[_], A](qp: c.Expr[QuasiIO[F]], expr: c.Expr[A]): c.Expr[F[A]] = {
    c.Expr[F[A]](q"$qp.maybeSuspend($expr)")
  }

  def logMethod[A](
    mode: EncodingMode,
    prefixName: TermName,
    self: c.Expr[AbstractLogger],
    level: c.Expr[Level],
    logTypes: c.Expr[Boolean],
    logImplicits: c.Expr[Boolean],
    function: c.Expr[A],
  ): c.Expr[A] = {
    val (variables, fnMessageTree, argsMsgTree, typesMsgTree, implicitsMsgTree) = createVariablesAndLogStringTrees(mode, function.tree)

    c.Expr[A](q"""
      val $prefixName = ${c.prefix}
      val self = $self
      val position = ${CodePositionMaterializerMacro.getEnclosingPosition(c)}
      try {
        val result = $function
        if (self.acceptable(position.get, $level)) {
          ..$variables
          val argsMsg = $argsMsgTree
          val typesMsg = ${ifOrEmptyMsg(logTypes)(typesMsgTree)}
          val implicitsMsg = ${ifOrEmptyMsg(logImplicits)(implicitsMsgTree)}
          self.unsafeLog(_root_.izumi.logstage.api.Log.Entry.create(
            $level,
            $fnMessageTree ++ typesMsg ++ argsMsg ++ implicitsMsg ++ ${messageMacro(mode, q""" " => " + result """)}
            )(position))
        }
        result
      } catch {
        case error: _root_.java.lang.Throwable =>
          if (self.acceptable(position.get, $level)) {
            ..$variables
            val argsMsg = $argsMsgTree
            val typesMsg = ${ifOrEmptyMsg(logTypes)(typesMsgTree)}
            val implicitsMsg = ${ifOrEmptyMsg(logImplicits)(implicitsMsgTree)}
            self.unsafeLog(_root_.izumi.logstage.api.Log.Entry.create(
              $level,
              $fnMessageTree ++ typesMsg ++ argsMsg ++ implicitsMsg ++ ${messageMacro(mode, q""" " => " + error """)}
              )(position))
          }
          throw error
      }
      """)
  }

  def logMethodIO[XF[_], F[x] >: XF[x], QP <: QuasiPrimitives[F], A](
    mode: EncodingMode,
    prefixName: TermName,
    qpExpr: c.Expr[QP],
    self: c.Expr[AbstractLogIO[XF]],
    level: c.Expr[Level],
    logTypes: c.Expr[Boolean],
    logImplicits: c.Expr[Boolean],
    functionTreeToInspect: Tree,
  )(functionToUse: c.Expr[QP] => c.Expr[F[A]]
  ): c.Expr[F[A]] = {
    // evaluate QuasiPrimitives just once. Avoid re-evaluating its derivation multiple times in runtime
    val qpName = c.freshName(TermName("F"))
    val (variables, fnMessageTree, argsMsgTree, typesMsgTree, implicitsMsgTree) = createVariablesAndLogStringTrees(mode, functionTreeToInspect)

    c.Expr[F[A]](q"""
      val $prefixName = ${c.prefix}
      val self = $self
      val position = ${CodePositionMaterializerMacro.getEnclosingPosition(c)}
      val $qpName = $qpExpr
      $qpName.tapBothUntyped(${functionToUse(c.Expr[QP](q"$qpName"))})(
        err = error0 => self.log($level)({
          ..$variables
          val argsMsg = $argsMsgTree
          val typesMsg = ${ifOrEmptyMsg(logTypes)(typesMsgTree)}
          val implicitsMsg = ${ifOrEmptyMsg(logImplicits)(implicitsMsgTree)}
          val errorMsg = error0 match {
            case error: Throwable =>
              ${messageMacro(mode, q""" " => " + error """)}
            case error: Any =>
              ${messageMacro(mode, q""" " => " + error """)}
          }
          $fnMessageTree ++ typesMsg ++ argsMsg ++ implicitsMsg ++ errorMsg
        })(position),
        succ = result => self.log($level)({
          ..$variables
          val argsMsg = $argsMsgTree
          val typesMsg = ${ifOrEmptyMsg(logTypes)(typesMsgTree)}
          val implicitsMsg = ${ifOrEmptyMsg(logImplicits)(implicitsMsgTree)}
          $fnMessageTree ++ typesMsg ++ argsMsg ++ implicitsMsg ++ ${messageMacro(mode, q""" " => " + result """)}
        })(position)
      )
      """)
  }

  private def createVariablesAndLogStringTrees(mode: EncodingMode, function: Tree): (List[Tree], Tree, Tree, Tree, Tree) = {
    val (method, argumentsTreess) = getFunctionArgumentsAndMethodSymbol(function)
    val argumentsTrees = argumentsTreess.flatten

    val (explicitArgNamess, implicitArgNamess) = getArgumentsToLog(method)

    val termVariableDecls = createVariablesTrees(explicitArgNamess.flatten ++ implicitArgNamess.flatten, argumentsTrees)
    val (typeVariableDecls, typesMessage) = mkTypesMsg(mode, function, method)

    val variableDecls = termVariableDecls ++ typeVariableDecls
    val fnMessage = q"_root_.izumi.logstage.api.Log.Message.raw(${s"Call to ${method.name.decodedName.toString}"})"
    val argsMessage = messageMacro(mode, mkParametersString(explicitArgNamess, "(", ")"))
    val implicitsMessage = messageMacro(mode, mkParametersString(implicitArgNamess, "(using ", ")"))

    (variableDecls, fnMessage, argsMessage, typesMessage, implicitsMessage)
  }

  private def mkTypesMsg(mode: EncodingMode, funcTree: Tree, methodSymbol: MethodSymbol): (List[Tree], Tree) = {
    val typeArguments = methodSymbol.typeParams.map(_.name)
    if (typeArguments.nonEmpty) {
      val typesPassed = getFunctionTypeArguments(funcTree)
      val typeVariableNames = typeArguments.map(_.toTermName)
      val typeVariableValues = typesPassed.map(t => q"${show(t)}")
      val typeVariableDecls = createVariablesTrees(typeVariableNames, typeVariableValues)
      val stringTree = mkParametersString(List(typeVariableNames), "[", "]")
      (typeVariableDecls, messageMacro(mode, stringTree))
    } else {
      (Nil, emptyMessageTree)
    }
  }

  private def ifOrEmptyMsg(bool: c.Expr[Boolean])(message: Tree): Tree = {
    q"if ($bool) $message else $emptyMessageTree"
  }

  private def getArgumentsToLog(
    methodSymbol: MethodSymbol,
  ): (List[List[TermName]], List[List[TermName]]) = {
    val methodArgumentss = methodSymbol.paramLists
    val (implicitArgumentss, explicitArgumentss) = methodArgumentss.partition(_.exists(_.isImplicit))

    val explicitArgumentNamess = explicitArgumentss.map(_.map(_.name.toTermName))
    val implicitArgumentNamess = implicitArgumentss.map(_.map(_.name.toTermName))

    (explicitArgumentNamess,  implicitArgumentNamess)
  }

  private def createVariablesTrees(argumentsNames: List[TermName], args: List[Tree]): List[Tree] = {
    argumentsNames.iterator.zip(args.iterator).map { case (name, arg) => q"lazy val $name: ${arg.tpe match { case null => null; case t => t.widen }} = $arg" }.toList
  }

  private def mkParametersString(valsNamess: List[List[TermName]], bracketOpen: String, bracketClose: String): Tree = {
    val bOpenExpr: Tree = Literal(Constant(bracketOpen))
    val bCloseExpr: Tree = Literal(Constant(bracketClose))
    val start: Tree = Literal(Constant(""))
    valsNamess.foldLeft(start) {
      (acc, valNames) =>
        val openedBracket = if (acc eq start) bOpenExpr else q""" $acc + $bOpenExpr """
        val withArgs = valNames match {
          case Nil => openedBracket
          case head :: tail => tail.foldLeft[Tree](q"$openedBracket + $head")((a, b) => q""" $a + ", " + $b """)
        }
        q""" $withArgs + $bCloseExpr """
    }
  }

  private def getFunctionTypeArguments(funcTree: Tree): List[Type] = {
    @tailrec
    def loop(tree: Tree): List[Type] = tree match {
      case TypeApply(_, targs) => targs.map(_.tpe)
      case Apply(fun, _) => loop(fun)
      case Block(List(), inner) => loop(inner)
      case _ => Nil
    }
    loop(funcTree)
  }

  private def getFunctionArgumentsAndMethodSymbol(funcTree: Tree): (MethodSymbol, List[List[Tree]]) = {
    @tailrec
    def loop(tree: Tree, argss: List[List[Tree]]): (MethodSymbol, List[List[Tree]]) = tree match {
      case Apply(m: SelectApi, args) => (m.symbol.asMethod, args :: argss)
      case Apply(TypeApply(m: SelectApi, _), args) => (m.symbol.asMethod, args :: argss)

      case Apply(inner, args) => loop(inner, args :: argss)
      case Apply(TypeApply(inner, _), args) => loop(inner, args :: argss)

      case Block(List(), inner) => loop(inner, argss)

      case _ => c.abort(c.enclosingPosition, s"Expected method call, but got ${showCode(tree)} (raw=${showRaw(tree)})")
    }
    loop(funcTree, Nil)
  }
}
