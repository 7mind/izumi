package com.github.pshirshov.izumi.idealingua.translator.toscala


import scala.meta.{Case, Defn, Pat, Stat, Term, Type}
import scala.meta._

case class ServiceMethodProduct(
                                 name: String
                                 , input: Type
                                 , output: Type
                                 , types: Seq[Defn]
                               ) {
  def defn: Stat = {
    q"def ${Term.Name(name)}(input: $input): Result[$output]"
  }

  def defnDispatch: Stat = {
    q"def ${Term.Name(name)}(input: $input): Result[$output] = dispatcher.dispatch(input, classOf[$output])"
  }

  def routingClause: Case = {
    Case(
      Pat.Typed(Pat.Var(Term.Name("value")), input)
      , None
      , q"service.${Term.Name(name)}(value)"
    )
  }
}
