package com.github.pshirshov.izumi.fundamentals.reflection.macrortti

import com.github.pshirshov.izumi.fundamentals.reflection.macrortti.LightTypeTag.{AbstractReference, FullReference, Lambda, LambdaParameter, NameReference, TypeParam}

protected[macrortti] object RuntimeAPI {

  def applyLambda(lambda: Lambda, parameters: Map[String, AbstractReference]): AbstractReference = {
    val newParams = lambda.input.filterNot(p => parameters.contains(p.name))
    val replaced = replace(lambda.output, parameters)

    if (newParams.isEmpty) {
      replaced
    } else {
      val renamed = newParams.zipWithIndex.map {
        case (p, idx) =>
          p.name -> idx.toString
      }
      val nr = newParams.zipWithIndex.map {
        case (p, idx) =>
          LambdaParameter(idx.toString, p.kind)
      }
      Lambda(nr, replace1(replaced, renamed.toMap))
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

  private def replace1(ref: AbstractReference, xparameters: Map[String, String]): AbstractReference = {
    ref match {
      case l: Lambda =>
        l
      case n@NameReference(ref) =>
        xparameters.get(ref) match {
          case Some(value) =>
            NameReference(value)
          case None =>
            n
        }

      case FullReference(ref, parameters) =>
        val p = parameters.map {
          case TypeParam(ref, kind, variance) =>
            TypeParam(replace1(ref, xparameters), kind, variance)
        }
        FullReference(ref, p)
    }
  }

}
