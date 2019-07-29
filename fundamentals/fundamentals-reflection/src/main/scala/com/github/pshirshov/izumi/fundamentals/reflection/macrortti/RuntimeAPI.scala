package com.github.pshirshov.izumi.fundamentals.reflection.macrortti

import com.github.pshirshov.izumi.fundamentals.reflection.macrortti.LightTypeTag.{AbstractReference, FullReference, Lambda, LambdaParameter, NameReference, TypeParam}

protected[macrortti] object RuntimeAPI {

  def applyLambda(lambda: Lambda, parameters: Map[String, AbstractReference]): AbstractReference = {
    val newParams = lambda.input.filterNot(p => parameters.contains(p.name))
    val replaced = replaceRefs(lambda.output, parameters)

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
      Lambda(nr, replaceRefNames(replaced, renamed.toMap))
    }
  }

  private def replaceRefs(ref: AbstractReference, xparameters: Map[String, AbstractReference]): AbstractReference = {
    ref match {
      case l: Lambda =>
        l
      case n@NameReference(ref, _) =>
        xparameters.get(ref) match {
          case Some(value) =>
            value
          case None =>
            n
        }

      case FullReference(ref, prefix, parameters) =>
        val p = parameters.map {
          case TypeParam(ref, kind, variance) =>
            TypeParam(replaceRefs(ref, xparameters), kind, variance)
        }
        FullReference(ref, prefix, p)
    }
  }

  private def replaceRefNames(ref: AbstractReference, xparameters: Map[String, String]): AbstractReference = {
    ref match {
      case l: Lambda =>
        l
      case n@NameReference(ref, prefix) =>
        xparameters.get(ref) match {
          case Some(value) =>
            NameReference(value, prefix)
          case None =>
            n
        }

      case FullReference(ref, prefix, parameters) =>
        val p = parameters.map {
          case TypeParam(ref, kind, variance) =>
            TypeParam(replaceRefNames(ref, xparameters), kind, variance)
        }
        FullReference(ref, prefix, p)
    }
  }

}
