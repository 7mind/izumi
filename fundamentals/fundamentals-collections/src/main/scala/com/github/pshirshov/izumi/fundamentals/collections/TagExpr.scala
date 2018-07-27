package com.github.pshirshov.izumi.fundamentals.collections


object TagExpr {

  sealed trait For[T] {

    sealed trait Expr {
      def evaluate(tags: Set[T]): Boolean = For.this.evaluate(tags, this)
    }

    sealed trait Composite extends Expr {
      def head: Expr

      def tail: Seq[Expr]

      def all: Seq[Expr] = head +: tail
    }

    // Can't keep fina because of https://issues.scala-lang.org/browse/SI-4440
    case class Has(tag: T) extends Expr

    case class Not(expr: Expr) extends Expr

    case class And(head: Expr, tail: Expr*) extends Composite

    case class Or(head: Expr, tail: Expr*) extends Composite

    case class Xor(head: Expr, tail: Expr*) extends Composite

    def evaluate(tags: Set[T], expr: Expr): Boolean = {
      expr match {
        case Has(tag) => tags.contains(tag)
        case Not(e) => !evaluate(tags, e)
        case and: And => and.all.forall(evaluate(tags, _))
        case or: Or => or.all.exists(evaluate(tags, _))
        case xor: Xor => xor.all.count(evaluate(tags, _)) == 1
      }
    }

    def any(head: T, tail: T*): Or = {
      Or(Has(head), tail.map(Has): _*)
    }

    def all(head: T, tail: T*): And = {
      And(Has(head), tail.map(Has): _*)
    }

    def one(head: T, tail: T*): Xor = {
      Xor(Has(head), tail.map(Has): _*)
    }
  }

  object Strings extends For[String] {

  }

}


