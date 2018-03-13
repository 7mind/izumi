package com.github.pshirshov.izumi.functional.maybe

sealed trait Result[+R]

object Result {
  case class Success[+R](value: R, trace: Trace[R]) extends Result[R]

  case class Failure[+R](value: Throwable, trace: Trace[R]) extends Result[R]

  case class Problem[+R](value: Problematic, trace: Trace[R]) extends Result[R]

}
