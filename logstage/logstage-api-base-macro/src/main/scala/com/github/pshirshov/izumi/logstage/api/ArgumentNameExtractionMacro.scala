package com.github.pshirshov.izumi.logstage.api


import scala.reflect.macros.blackbox


object ArgumentNameExtractionMacro {
  protected[api] def recoverArgNames(c: blackbox.Context)(args: Seq[c.Expr[Any]]): c.Expr[List[(String, Any)]] = {
    import c.universe._

    object ArrowArg {

      import c.universe._

      def unapply(arg: c.universe.Tree): Option[(c.Expr[Any], String)] = {
        arg match {
          case Apply
            (
            TypeApply
              (
              Select
                (
                Apply
                  (
                  TypeApply(Select(Select(Ident(TermName("scala")), _), TermName("ArrowAssoc")), List(TypeTree())),
                  expr :: Nil
                  ),
                TermName("$minus$greater")
                )
              , List(TypeTree())
              )
            , List(Literal(Constant(name: String)))
            ) =>
            Some((c.Expr(expr), name))
          case _ =>
            None
        }
      }
    }

    val expressions = args.map {
      param =>
        param.tree match {
          case c.universe.Ident(TermName(s)) =>
            reifiedExtracted(c)(param, s)

          case ArrowArg(expr, name) =>
            reifiedExtracted(c)(expr, name)

          case c.universe.Select(_, TermName(s)) =>
            reifiedExtracted(c)(param, s)

          case c.universe.Literal(c.universe.Constant(v)) =>
            c.warning(c.enclosingPosition,
              s"""Constant expression as a logger argument: $v, this makes no sense.""".stripMargin)

            reifiedPrefixed(c)(param, "UNNAMED")

          case v =>
            c.warning(c.enclosingPosition,
              s"""Expression as a logger argument: $v
                  |
                  |Izumi logger expect you to provide plain variables or names expressions as arguments:
                  |1) Simple variable logger.log(s"My message: $$argument")
                  |2) Named expression logger.log(s"My message: $${Some.expression -> "argname"}")
               """.stripMargin)
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

