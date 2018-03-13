package com.github.pshirshov.izumi.functional.maybe

import com.github.pshirshov.izumi.functional.maybe.Result.{Failure, Problem, Success}

trait Problematic

//type Maybe[R] = Either[R, Problematic] // throw or return?

object X {

}

trait Safe[R] {
  type UR
  type UPSTREAM = Safe[UR]

  def flatMap[R1](f: R => Safe[R1]): Safe[R1] = new FChained[R1, R](this, f)

  def map[R1](f: R => R1): Safe[R1] = new MChained[R1, R](this, f)

  def run(): Result[R]

  protected def upstream: UPSTREAM

  protected def mapDo[R0, U0](upstream: Safe[U0])(handler: U0 => Result[R0]): Result[R0] = {
    val upstreamResult = upstream.run()

    upstreamResult match {
      case Success(upstreamV, upstreamT) =>
        handler(upstreamV) match {
          case s1@Success(sv, h1) =>
            Success(sv, upstreamT.prepend(List(s1).map(Trace.simplify) ++ h1.history))

          case f1@Problem(ft, h1) =>
            Problem(ft, upstreamT.prepend(h1.history))

          case f1@Failure(ft, h1) =>
            Failure(ft, upstreamT.prepend(h1.history))

        }

      case Problem(upstreamV, upstreamT) =>
        Problem(upstreamV, upstreamT.prepend(List(TNode.TPending())))

      case Failure(upstreamV, upstreamT) =>
        Failure(upstreamV, upstreamT.prepend(List(TNode.TPending())))
    }
  }

  protected def fmapDo[R0, U0](upstream: Safe[U0])(handler: U0 => Result[R0]): Result[R0] = {
    val upstreamResult = upstream.run()

    upstreamResult match {
      case Success(upstreamV, upstreamT) =>
        handler(upstreamV) match {
          case s1@Success(sv, h1) =>
            Success(sv, upstreamT.prepend(List(s1).map(Trace.simplify)))

          case f1@Problem(ft, h1) =>
            Problem(ft, upstreamT.prepend(h1.history))

          case f1@Failure(ft, h1) =>
            Failure(ft, upstreamT.prepend(h1.history))
        }

      case Problem(upstreamV, upstreamT) =>
        Problem(upstreamV, upstreamT.prepend(List(TNode.TPending())))

      case Failure(upstreamV, upstreamT) =>
        Failure(upstreamV, upstreamT.prepend(List(TNode.TPending())))
    }
  }

  protected def fresult(f: => Result[R]): Result[R] = {
    import Result._

    try {
      f match { // TODO: is it a crutch?..
        case Success(v: Problematic, t) =>
          Problem(v, t)

        case Success(v: Throwable, t) =>
          Failure(v, t)

        case Success(v, t) =>
          Success(v, t)

        case value =>
          value
      }
    } catch {
      case t: Problematic => Problem(t, Trace(List(TNode.TProblem(t))))
      case t: Throwable => Failure(t, Trace(List(TNode.TFailure(t))))
    }
  }

  protected def result(f: => R): Result[R] = {
    import Result._

    try {
      Success(f, Trace.empty)
    } catch {
      case t: Problematic => Problem(t, Trace(List(TNode.TProblem(t))))
      case t: Throwable => Failure(t, Trace(List(TNode.TFailure(t))))
    }
  }
}

class FChained[R, U0]
(
  override val upstream: Safe[U0]
  , f: U0 => Safe[R]
) extends Safe[R] {
  override type UR = U0

  override def run(): Result[R] = fmapDo[R, U0](upstream) {
    upstreamV =>
      fresult {
        f(upstreamV).run()
      }
  }
}

class MChained[R, U0]
(
  override val upstream: Safe[U0]
  , f: U0 => R
) extends Safe[R] {
  override type UR = U0

  override def run(): Result[R] = mapDo[R, U0](upstream) {
    upstreamV =>
      result {
        f(upstreamV)
      }
  }
}


case object Void

class Initial[R](f: => R) extends Safe[R] {
  override def run(): Result[R] = {
    import Result._

    fresult {
      val r = f
      Success(r, Trace(List(TNode.TSuccess(r))))
    }
  }

  override type UR = Void.type

  protected override def upstream: Safe[UR] = new Initial(Void)
}

class FInitial[R](f: => Result[R]) extends Safe[R] {
  override def run(): Result[R] = {
    fresult {
      f
    }
  }

  override type UR = Void.type

  protected override def upstream: Safe[UR] = new Initial(Void)
}

object Safe {
  def apply[R](f: => R): Safe[R] = new Initial[R](f)

  def problem[R](f: => Problematic): Safe[R] = {
    new FInitial[R](Result.Problem(f, Trace.empty))
  }

  def failure[R](f: => Throwable): Safe[R] = {
    new FInitial[R](Result.Failure(f, Trace.empty))
  }
}

