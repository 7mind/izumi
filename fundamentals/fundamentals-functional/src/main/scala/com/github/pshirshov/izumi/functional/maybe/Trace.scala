package com.github.pshirshov.izumi.functional.maybe

case class Trace[+R](history: List[TNode[_]]) { // TODO: HList?..
  def prepend[T](result: List[TNode[_]]): Trace[T] = {
    Trace[T](result ++ history)
  }

  def append[T](result: List[TNode[_]]): Trace[T] = {
    Trace[T](history ++ result)
  }

}

object Trace {
  def simplify[R](result: Result[R]): TNode[R] = {
    import Result._

    result match {
      case Success(v, _) => TNode.TSuccess(v)
      case Failure(f, _) => TNode.TFailure(f)
      case Problem(p, _) => TNode.TProblem(p)
    }
  }

  def empty[R]: Trace[R] = Trace(List.empty)

}
