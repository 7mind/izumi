package com.github.pshirshov.izumi.idealingua.model.common

object PackageTools {
  def minimize(value: Package, in: Package): Package = {
    value.map(Option.apply)
      .zipAll(in.map(Option.apply), None, None)
      .filterNot(pair => pair._1 == pair._2).flatMap(_._1)
  }
}
