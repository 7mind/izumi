package com.github.pshirshov.izumi.fundamentals.reflection.macrortti

import com.github.pshirshov.izumi.fundamentals.reflection.macrortti.LightTypeTag.{AbstractReference, FullReference, Lambda, NameReference, TypeParam}

object RuntimeAPI {

  def applyLambda(lambda: Lambda, parameters: Map[String, AbstractReference]): AbstractReference = {
    val newParams = lambda.input.filterNot(p => parameters.contains(p.idx.toString))
    val replaced = replace(lambda.output, parameters)

    if (newParams.isEmpty) {
      replaced
    } else {
      Lambda(newParams, replaced)
    }
  }

  private def replace(ref: AbstractReference, xparameters: Map[String, AbstractReference]): AbstractReference = {
    ref match {
      case l: Lambda =>
        l
      case n@NameReference(ref) =>
        xparameters.get(ref) match {
          case Some(value) =>
            value
          case None =>
            n
        }

      case FullReference(ref, parameters) =>
        val p = parameters.map {
          case TypeParam(ref, kind, variance) =>
            TypeParam(replace(ref, xparameters), kind, variance)
        }
        FullReference(ref, p)
    }
  }


}
