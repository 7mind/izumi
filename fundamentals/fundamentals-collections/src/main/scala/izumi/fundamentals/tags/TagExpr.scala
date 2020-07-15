package izumi.fundamentals.tags

import scala.annotation.tailrec
import scala.reflect.ClassTag

object TagExpr {

  trait For[T] {

    sealed trait Expr {
      def evaluate(tags: Set[T]): Boolean = For.this.evaluate(tags, this)
    }

    object Expr {

      implicit final class TOps(t: T) {

        private val singleTagEpr = Has(t)

        def &&[O <: Expr](o: O): And = {
          And(Set(singleTagEpr, o))
        }

        def ||[O <: Expr](o: O): Or = {
          Or(Set(singleTagEpr, o))
        }

        def ^^[O <: Expr](o: O): Xor = {
          Xor(Set(singleTagEpr, o))
        }

        def unary_! : Not = {
          Not(singleTagEpr)
        }

        def &&(o: T): And = {
          And(Set(singleTagEpr, Has(o)))
        }

        def ||(o: T): Or = {
          Or(Set(singleTagEpr, Has(o)))
        }

        def ^^(o: T): Xor = {
          Xor(Set(singleTagEpr, Has(o)))
        }
      }

      implicit class ExprOps(e: Expr) {
        def &&[O <: Expr](o: O): And = {
          And(Set(e, o))
        }

        def ||[O <: Expr](o: O): Or = {
          Or(Set(e, o))
        }

        def ^^[O <: Expr](o: O): Xor = {
          Xor(Set(e, o))
        }

        def unary_! : Not = {
          Not(e)
        }
      }

    }

    sealed trait Composite extends Expr {
      def all: Set[Expr]

      protected def mark: String

      override def toString: String = if (all.size == 1) {
        all.head.toString
      } else {
        all.toSeq.map(_.toString).sorted.mkString("(", s" $mark ", ")")
      }
    }

    sealed trait Const extends Expr

    case object True extends Const {
      override def toString: String = s"true"
    }

    case object False extends Const {
      override def toString: String = s"false"
    }

    // Can't keep final because of https://issues.scala-lang.org/browse/SI-4440
    case class Has(tag: T) extends Expr {
      override def toString: String = s":$tag"
    }

    case class Not(expr: Expr) extends Expr {
      override def toString: String = s"!$expr"
    }

    case class And(all: Set[Expr]) extends Composite {
      override protected def mark: String = "&&"
    }

    case class Or(all: Set[Expr]) extends Composite {
      override protected def mark: String = "||"
    }

    case class Xor(all: Set[Expr]) extends Composite {
      override protected def mark: String = "\\/"
    }

    def evaluate(tags: Set[T], expr: Expr): Boolean = {
      expr match {
        case Has(tag) => tags.contains(tag)
        case Not(e) => !evaluate(tags, e)
        case True => true
        case False => false
        case and: And => and.all.forall(evaluate(tags, _))
        case or: Or => or.all.exists(evaluate(tags, _))
        case xor: Xor => xor.all.count(evaluate(tags, _)) == 1
      }
    }

    def any(head: T, tail: T*): Or = {
      Or((head +: tail).toSet.map(Has))
    }

    def all(head: T, tail: T*): And = {
      And((head +: tail).toSet.map(Has))
    }

    def one(head: T, tail: T*): Xor = {
      Xor((head +: tail).toSet.map(Has))
    }

    object TagDNF {

      def toDNF(e: Expr): Expr = {
        e match {
          case v: Composite if v.all.size == 1 =>
            toDNF(v.all.head)

          case v @ Not(_: Composite) =>
            toDNF(distributionLaw(v))

          case v @ Not(_) =>
            distributionLaw(v)

          case v: Or =>
            doOr(v.all.map(toDNF))

          case v: Xor =>
            toDNF(xorExplode(v))

          case v: And =>
            val conjunction = doAnd(v.all.map(toDNF))

            conjunction match {
              case a: And =>
                val (disjunctions, conjunctions) = a.all.foldLeft((List.empty[Or], Set.empty[Expr])) {
                  case (acc, c: Or) => (acc._1 :+ c, acc._2)
                  case (acc, c) => (acc._1, acc._2 + c)
                }

                disjunctions match {
                  case Nil =>
                    conjunction
                  case head :: tail =>
                    toDNF(deMorganLaw(head, conjunctions ++ tail))
                }

              case o => o
            }

          case v =>
            v
        }
      }

      private def xorExplode(v: Xor): Or = {
        val pairs: Seq[(Expr, Set[Expr])] = {
          val asSeq = v.all.toSeq

          (1 to v.all.size).map {
            idx =>
              val (left, right) = asSeq.splitAt(idx)
              (left.last, (left.init ++ right).toSet)
          }
        }

        val clauses = pairs.map {
          case (pos, neg) => And(Set(pos, Not(Or(neg))))
        }
        Or(clauses.toSet)
      }

      @inline private def deMorganLaw(disjunction: Or, conjunctions: Set[Expr]): Or = {
        val exprs = disjunction.all.map(d => doAnd(conjunctions + d))
        Or(exprs)
      }

      @tailrec
      private def distributionLaw(v: Not): Expr = {
        v.expr match {
          case _: Has => v
          case True => False
          case False => True
          case se: Not => se.expr
          case se: Or => doAnd(se.all.map(Not))
          case se: And => doOr(se.all.map(Not))
          case se: Xor => distributionLaw(Not(xorExplode(se)))
        }
      }

      @inline private def doAnd(all: Set[Expr]): Expr = {
        val withoutTrue = all.filterNot(_ == True)
        doAgg[And](withoutTrue, v => And(v)) match {
          case v: And if containsPair(v) => False
          case v => v
        }
      }

      @inline private def doOr(all: Set[Expr]): Expr = {
        val withoutFalse = all.filterNot(_ == False)
        doAgg[Or](withoutFalse, v => Or(v)) match {
          case v: Or if containsPair(v) => True
          case v => v
        }
      }

      @inline private def containsPair(a: Composite): Boolean = {
        a.all.exists(e => a.all.contains(Not(e)))
      }

      @inline private def doAgg[TT <: Composite: ClassTag](all: Set[Expr], c: Set[Expr] => TT): Expr = {
        all.flatMap {
          case a: TT => a.all
          case v => Set(v)
        } match {
          case e if e.size == 1 => e.head
          case e => c(e)
        }
      }
    }

  }

  object Strings extends For[String] {

    implicit class C(val sc: StringContext) {
      def t(args: Any*): Expr = Has(sc.s(args: _*))
    }

  }

}
