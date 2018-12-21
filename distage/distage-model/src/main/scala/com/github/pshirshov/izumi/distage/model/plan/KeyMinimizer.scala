package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._

import scala.util.{Failure, Success, Try}

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

  private val trFull = new TypeRenderer(getName)

  private def getName(tpe: RuntimeDIUniverse.TypeNative) = {
    val shortname = tpe.typeSymbol.name.toString

    val by = indexed.getOrElse(shortname, 0)
    if (by == 1) {
      shortname
    } else {
      tpe.typeSymbol.fullName
    }
  }

  def renderType(tpe: Class[_]): String = {
    Try(u.runtimeMirror(tpe.getClassLoader).classSymbol(tpe)).map(_.toType) match {
      case Failure(_) =>
        tpe.getTypeName
      case Success(value) =>
        renderType(SafeType.apply(value))
    }
  }

  def renderType(tpe: TypeNative): String = {
    trFull.renderType(tpe)
  }

  def renderType(tpe: SafeType): String = {
    trFull.renderType(tpe)
  }

  def render(key: DIKey): String = {
    render(key, renderType)
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
    (key +: key.tpe.typeArgs.map(SafeType.apply)).toSet
  }

  private class TypeRenderer(getName: TypeNative => String) {
    def renderType(tpe: SafeType): String = {
      renderType(tpe.tpe)
    }

    def renderType(tpe: TypeNative): String = {
      val args = if (tpe.typeArgs.nonEmpty) {
        tpe.typeArgs.map(renderType).mkString("[", ",", "]")
      } else {
        ""
      }
      getName(tpe) + args
    }
  }


}
