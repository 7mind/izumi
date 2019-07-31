package com.github.pshirshov.izumi.fundamentals.reflection.macrortti

import com.github.pshirshov.izumi.fundamentals.reflection.macrortti.LightTypeTag.{AbstractReference, AppliedNamedReference, FullReference, IntersectionReference, Lambda, LambdaParameter, NameReference, TypeParam}

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

  private def replaceRefs(reference: AbstractReference, xparameters: Map[String, AbstractReference]): AbstractReference = {
    reference match {
      case l: Lambda =>
        l
      case IntersectionReference(refs) =>
        IntersectionReference(refs.map(replaceRefs(_, xparameters).asInstanceOf[AppliedNamedReference]))
      case n@NameReference(ref, _) =>
        xparameters.get(ref) match {
          case Some(value) =>
            value
          case None =>
            n
        }

      case FullReference(ref, prefix, parameters) =>
        val p = parameters.map {
          case TypeParam(pref, kind, variance) =>
            TypeParam(replaceRefs(pref, xparameters), kind, variance)
        }
        FullReference(ref, prefix, p)
    }
  }

  private def replaceRefNames(reference: AbstractReference, xparameters: Map[String, String]): AbstractReference = {

    reference match {
      case l: Lambda =>
        l
      case IntersectionReference(refs) =>
        IntersectionReference(refs.map(replaceRefNames(_, xparameters).asInstanceOf[AppliedNamedReference]))
      case n@NameReference(ref, prefix) =>
        xparameters.get(ref) match {
          case Some(value) =>
            NameReference(value, prefix)
          case None =>
            n
        }

      case FullReference(ref, prefix, parameters) =>
        val p = parameters.map {
          case TypeParam(pref, kind, variance) =>
            TypeParam(replaceRefNames(pref, xparameters), kind, variance)
        }
        FullReference(ref, prefix, p)
    }
  }

}
