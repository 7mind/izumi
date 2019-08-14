package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._

class KeyMinimizer(allKeys: Set[DIKey]) {
  def renderType(tpe: TypeNative): String = {
    trFull.renderType(tpe)
  }

  def renderType(tpe: SafeType): String = {
    trFull.renderType(tpe)
  }

  def render(key: DIKey): String = {
    render(key, renderType)
  }

  private val indexed: Map[String, Int] = allKeys
    .toSeq
    .flatMap(extract)
    .map {
      k =>
        k.tpe.typeSymbol.name.toString -> k.tpe.typeSymbol
    }
    .groupBy(_._1)
    .mapValues(_.map(_._2).toSet.size)
    .toMap

  private val trFull = new TypeRenderer(getName)

  private def getName(tpe: RuntimeDIUniverse.TypeNative): String = {
    val shortname = tpe.typeSymbol.name.toString

    val by = indexed.getOrElse(shortname, 0)
    if (by == 1) {
      shortname
    } else {
      tpe.typeSymbol.fullName
    }
  }


  private def render(key: DIKey, rendertype: SafeType => String): String = {
    // in order to make idea links working we need to put a dot before Position occurence and avoid using #
    key match {
      case DIKey.TypeKey(tpe) =>
        s"{type.${rendertype(tpe)}}"

      case DIKey.IdKey(tpe, id) =>
        val asString = id.toString
        val fullId = if (asString.contains(":") || asString.contains("#")) {
          s"[$asString]"
        } else {
          asString
        }

        s"{id.${rendertype(tpe)}@$fullId}"

      case DIKey.ProxyElementKey(proxied, _) =>
        s"{proxy.${render(proxied)}}"

      case DIKey.SetElementKey(set, reference) =>
        s"{set.${render(set)}/${render(reference)}}"
    }
  }

  private def extract(key: DIKey): Set[SafeType] = {
    key match {
      case k: DIKey.TypeKey =>
        extract(k.tpe)

      case k: DIKey.IdKey[_] =>
        extract(k.tpe)

      case p: DIKey.ProxyElementKey =>
        extract(p.tpe) ++ extract(p.proxied)

      case s: DIKey.SetElementKey =>
        extract(s.tpe) ++ extract(s.reference)
    }
  }

  private def extract(key: SafeType): Set[SafeType] = {
    val params = key.tpe.dealias.finalResultType.typeArgs.map(_.dealias.finalResultType).map(SafeType.apply).flatMap(extract)
    Set(key) ++ params
  }

  private class TypeRenderer(getName: TypeNative => String) {
    def renderType(tpe: SafeType): String = {
      renderType(tpe.tpe)
    }

    def renderType(tpe: TypeNative): String = {
      val resultType = tpe.dealias.finalResultType
      val targs = resultType.typeArgs
      val tparams = resultType.typeParams

      val args = if (targs.nonEmpty && tparams.isEmpty) {
        targs.map(_.dealias.finalResultType)
          .map {
            t =>
              if (t.typeSymbol.isParameter) {
                "?"
              } else {
                renderType(t)
              }
          }
      } else if (targs.isEmpty && tparams.nonEmpty) {
        List.fill(tparams.size)("?")
      } else if (targs.nonEmpty && tparams.nonEmpty) {
        List("<?>")
      } else {
        List.empty
      }

      val asList = if (args.forall(_ == "?")) {
        ""
      } else {
        args.mkString("[", ",", "]")
      }

      getName(tpe) + asList
    }
  }


}
