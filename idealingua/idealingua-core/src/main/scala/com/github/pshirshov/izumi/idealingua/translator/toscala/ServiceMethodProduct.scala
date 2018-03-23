package com.github.pshirshov.izumi.idealingua.translator.toscala


import scala.meta.{Case, Defn, Pat, Stat, Term, Type}
import scala.meta._

case class ServiceMethodProduct(
                                 name: String
                                 , in: CompositeStructure
                                 , out: CompositeStructure
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

  def defnExplode: Stat = {
    val code = in.explodedSignature.map(p => q"${Term.Name(p.name.value)} = input.${Term.Name(p.name.value)}")
    q"def ${Term.Name(name)}(input: $input): Result[$output] = ${Term.Name(name)}(..$code)"
  }

  def defnExploded: Stat = {
    q"def ${Term.Name(name)}(..${in.explodedSignature}): Result[$output]"
  }


  def defnCompress: Stat = {
    val code = in.explodedSignature.map(p => q"${Term.Name(p.name.value)} = ${Term.Name(p.name.value)}")

    q"""def ${Term.Name(name)}(..${in.explodedSignature}): Result[$output] = {
       service.${Term.Name(name)}(${in.t.termFull}(..$code))
      }
      """
  }

  def routingClause: Case = {
    Case(
      Pat.Typed(Pat.Var(Term.Name("value")), input)
      , None
      , q"service.${Term.Name(name)}(value)"
    )
  }
}
