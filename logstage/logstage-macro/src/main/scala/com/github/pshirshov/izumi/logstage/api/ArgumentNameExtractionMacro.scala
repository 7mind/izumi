package com.github.pshirshov.izumi.logstage.api


import scala.language.experimental.macros
import scala.reflect.macros.blackbox


object ArgumentNameExtractionMacro {
  protected[api] def recoverArgNames(c: blackbox.Context)(args: Seq[c.Expr[Any]]): c.Expr[List[(String, Any)]] = {
    import c.universe._

    val expressions = args.map {
      param =>
        param.tree match {
          case c.universe.Ident(TermName(s)) =>
            reifiedExtracted(c)(param, s)

          case c.universe.Select(_, TermName(s)) =>
            reifiedExtracted(c)(param, s)

          case c.universe.Literal(c.universe.Constant(_)) =>
            reifiedPrefixed(c)(param, "UNNAMED")

          case v =>
            reifiedPrefixedValue(c)(c.Expr[String](Literal(Constant(v.toString()))), param, "EXPRESSION")
        }
    }

    c.Expr[List[(String, Any)]] {
      q"List(..$expressions)"
    }
  }

  private def reifiedPrefixed(c: blackbox.Context)(param: c.Expr[Any], prefix: String): c.universe.Expr[(String, Any)] = {
    reifiedPrefixedValue(c)(param, param, prefix)
  }

  private def reifiedPrefixedValue(c: blackbox.Context)(param: c.Expr[Any], value: c.Expr[Any], prefix: String): c.universe.Expr[(String, Any)] = {
    import c.universe._
    val prefixRepr = c.Expr[String](Literal(Constant(prefix)))
    reify {
      (s"${prefixRepr.splice}:${param.splice}", value.splice)
    }
  }


  private def reifiedExtracted(c: blackbox.Context)(param: c.Expr[Any], s: String): c.universe.Expr[(String, Any)] = {
    import c.universe._
    val paramRepTree = c.Expr[String](Literal(Constant(s)))
    reify {
      (paramRepTree.splice, param.splice)
    }
  }

}

