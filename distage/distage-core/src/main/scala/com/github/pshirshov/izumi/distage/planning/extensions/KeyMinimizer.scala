package com.github.pshirshov.izumi.distage.planning.extensions

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import distage.{DIKey, SafeType}

class KeyMinimizer(allKeys: Set[DIKey]) {
  private val indexed: Map[String, Int] = allKeys
    .toSeq
    .flatMap(extract)
    .map {
      k =>
        k.tpe.typeSymbol.name.toString -> k.tpe.typeSymbol
    }
    .groupBy(_._1)
    .mapValues(_.map(_._2).toSet.size)


  def render(key: DIKey): String = {
    val trFull = new TypeRenderer({
      tpe =>
        val shortname = tpe.typeSymbol.name.toString

        val by = indexed.getOrElse(shortname, 0)
        if (by == 1) {
          shortname
        } else {
          tpe.typeSymbol.fullName
        }

    })
    render(key, tpe => trFull.renderType(tpe))
  }

  private def render(key: DIKey, rendertype: SafeType => String): String = {
    key match {
      case DIKey.TypeKey(tpe) =>
        s"${rendertype(tpe)}"

      case DIKey.IdKey(tpe, id) =>
        val asString = id.toString
        val fullId = if (asString.contains(":") || asString.contains("#")) {
          s"($asString)"
        } else {
          asString
        }

        s"${rendertype(tpe)}#$fullId"

      case DIKey.ProxyElementKey(proxied, _) =>
        s"{${render(proxied)}}:proxy"

      case DIKey.SetElementKey(set, reference) =>
        s"{${render(set)}}:${render(reference)}"
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
    (key +: key.tpe.typeArgs.map(SafeType.apply)).toSet
  }

  class TypeRenderer(getName: RuntimeDIUniverse.TypeNative => String) {
    def renderType(tpe: SafeType): String = {
      renderType(tpe.tpe)
    }

    private def renderType(tpe: RuntimeDIUniverse.TypeNative): String = {
      val args = if (tpe.typeArgs.nonEmpty) {
        tpe.typeArgs.map(renderType).mkString("[", ",", "]")
      } else {
        ""
      }
      getName(tpe) + args
    }
  }


}
