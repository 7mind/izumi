package com.github.pshirshov.izumi.fundamentals.platform.exceptions

object IzStack {

  import IzThrowable._

  def currentStack: String = {
    new RuntimeException().format
  }

  def currentStack(acceptedPackages: Set[String]): String = {
    new RuntimeException().forPackages(acceptedPackages).format
  }
}
