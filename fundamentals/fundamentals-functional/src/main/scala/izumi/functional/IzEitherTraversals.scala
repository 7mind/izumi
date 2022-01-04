package izumi.functional

import izumi.functional.IzEitherTraversals.*
import scala.collection.compat.*
import scala.language.implicitConversions

trait IzEitherTraversals {
  @inline implicit final def EitherBiTraversals[Col[x] <: IterableOnce[x], T](col: Col[T]): EitherBiTraversals[Col, T] = new EitherBiTraversals(col)
  @inline implicit final def EitherLrPartitions[L, R, Col[x] <: IterableOnce[x]](col: Col[Either[L, R]]): EitherLrPartitions[L, R, Col] = new EitherLrPartitions(col)
}

object IzEitherTraversals extends IzEitherTraversals {
  final class EitherBiTraversals[Col[x] <: IterableOnce[x], T](private val col: Col[T]) extends AnyVal {
    def biFind[E](predicate: T => Either[E, Boolean]): Either[E, Option[T]] = {
      val i = col.iterator

      while (i.hasNext) {
        val a = i.next()
        predicate(a) match {
          case Left(value) =>
            return Left(value)
          case Right(true) =>
            return Right(Some(a))
          case Right(_) =>
        }
      }
      Right(None)
    }

    /** monadic `foldLeft` with error accumulation */
    def biFoldLeft[E, A](z: A)(op: (A, T) => Either[E, A]): Either[E, A] = {
      val i = col.iterator
      var acc: Either[E, A] = Right(z)

      while (i.hasNext && acc.isRight) {
        val nxt = i.next()
        (acc, nxt) match {
          case (Right(a), n) =>
            acc = op(a, n)
          case _ =>
        }
      }
      acc
    }

  }

  final class EitherLrPartitions[L, R, Col[x] <: IterableOnce[x]](col: Col[Either[L, R]]) {
    def lrPartition(implicit bl: Factory[L, Col[L]], br: Factory[R, Col[R]]): (Col[L], Col[R]) = {
      val bad = bl.newBuilder
      val good = br.newBuilder

      col.iterator.foreach {
        case Left(e) => bad += e
        case Right(v) => good += v
      }

      (bad.result(), good.result())
    }
  }
}
