package com.github.pshirshov.izumi.functional.result

import com.github.pshirshov.izumi.functional._

import scala.util.control.NonFatal

trait SafeBase[+P] {
  protected def pclass: Class[_]

  def fapply[R, R1, P1 >: P](value: R, f: R => result.Result[R1, P1]): result.Result[R1, P1] = {
    val IsProblem = RuntimeTest[P1](pclass.asInstanceOf[Class[P1]])

    try {
      f(value)
    } catch {
      case IsProblem(p) => new Result.Problem[R1, P1](p) {
        override protected val context: SafeBase[P] = SafeBase.this.asInstanceOf[SafeBase[P]]
      }
      case NonFatal(t) => new Result.Failure[R1, P1](t) {
        override protected val context: SafeBase[P] = SafeBase.this.asInstanceOf[SafeBase[P]]
      }
    }
  }

  //  def apply[R, P1 >: P](f: => Result[R, P1]): Result[R, P1] = {
  //    val value: Result[Result[R, P1], P] = apply(f)
  //    value.flatten
  //  }

  def apply[R](f: => R): result.Result[R, P] = {
    val IsProblem = RuntimeTest[P](pclass.asInstanceOf[Class[P]])

    try {
      new Result.Success[R, P](f) {
        override protected val context: SafeBase[P] = SafeBase.this
      }
    } catch {
      case IsProblem(p) => new Result.Problem[R, P](p) {
        override protected val context: SafeBase[P] = SafeBase.this
      }
      case NonFatal(t) => new Result.Failure[R, P](t) {
        override protected val context: SafeBase[P] = SafeBase.this
      }
    }
  }
}

