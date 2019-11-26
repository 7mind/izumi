package izumi.distage.model.plan.repr

import izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import izumi.fundamentals.collections.IzCollections._
import izumi.fundamentals.reflection.macrortti.LightTypeTagRef.NameReference
import izumi.fundamentals.reflection.macrortti.{LTTRenderables, LightTypeTagRef, RuntimeAPI}

class KeyMinimizer(allKeys: Set[DIKey]) {
  private val indexed: Map[String, Int] = allKeys
    .toSeq
    .flatMap(extract)
    .map(name => name.split('.').last -> name)
    .toMultimap
    .mapValues(_.size)
    .toMap

  private val minimizer = new LTTRenderables {
    protected def nameToString(value: NameReference): String = {
      val shortname = value.ref.name.split('.').last
      val by = indexed.getOrElse(shortname, 0)
      if (by <= 1) {
        shortname
      } else {
        value.ref.name
      }
    }
  }

  def render(key: DIKey): String = {
    render(key, renderType)
  }

  def renderType(tpe: SafeType): String = {
    import minimizer._
    tpe.tag.ref.render()
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

      case DIKey.SetElementKey(set, reference, disambiguator) =>
        s"{set.${render(set)}/${render(reference)}#${disambiguator.fold("0")(_.hashCode().toString)}"
    }
  }

  private def extract(key: DIKey): Set[String] = {
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

  private def extract(key: SafeType): Set[String] = {
    RuntimeAPI.unpack(key.tag.ref match {
      case reference: LightTypeTagRef.AbstractReference =>
        reference
    }).map(_.ref.name)
  }
}
