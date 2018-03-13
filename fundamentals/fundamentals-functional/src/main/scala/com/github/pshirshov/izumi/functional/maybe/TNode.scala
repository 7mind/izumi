package com.github.pshirshov.izumi.functional.maybe

sealed trait TNode[+R]

object TNode {

  case class TSuccess[+R](value: R) extends TNode[R]

  case class TFailure[+R](value: Throwable) extends TNode[R]

  case class TProblem[+R](value: Problematic) extends TNode[R]

  case class TPending[+R]() extends TNode[R]

}
