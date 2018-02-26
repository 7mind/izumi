package com.github.pshirshov.izumi.idealingua.model.common

object PackageTools {
  def minimize(value: Package, in: Package): Package = {

    val mapping = value.map(Option.apply)
      .zipAll(in.map(Option.apply), None, None)
        .map {
          case (l, r) => (l, r, l == r)
        }
      if (mapping.zip(in).forall(_._1._3)) {
        value.takeRight(value.size - in.size)
      } else {
        value
      }
  }
}
