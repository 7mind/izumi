package izumi.distage.model.plan.repr

import izumi.distage.model.reflection._
import izumi.fundamentals.collections.IzCollections._
import izumi.fundamentals.reflection.macrortti.LightTypeTagRef.SymName
import izumi.fundamentals.reflection.macrortti.{LTTRenderables, LightTypeTagRef, RuntimeAPI}

import com.github.ghik.silencer.silent

class KeyMinimizer(allKeys: Set[DIKey]) {

  @silent("msg=Unused import")
  private[this] val index: Map[String, Int] = {
    import scala.collection.compat._

    allKeys
      .toSeq
      .flatMap(extract)
      .map(name => name.split('.').last -> name)
      .toMultimap
      .view
      .mapValues(_.size)
      .toMap
  }

  private[this] val minimizedLTTRenderables = new LTTRenderables {
    override def r_SymName(sym: SymName, hasPrefix: Boolean): String = {
      val shortname = sym.name.split('.').last
      if (hasPrefix) {
        shortname
      } else {
        val withSameName = index.getOrElse(shortname, 0)
        if (withSameName <= 1) shortname else sym.name
      }
    }
  }

  def renderType(tpe: SafeType): String = {
    import minimizedLTTRenderables._
    tpe.tag.ref.render()
  }

  def renderKey(key: DIKey): String = renderKey(key, renderType)

  private[this] def renderKey(key: DIKey, rendertype: SafeType => String): String = {
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
        s"{proxy.${renderKey(proxied)}}"

      case DIKey.EffectKey(key, _) =>
        s"{effect.${renderKey(key)}"

      case DIKey.ResourceKey(key, _) =>
        s"{resource.${renderKey(key)}"

      case DIKey.SetElementKey(set, reference, disambiguator) =>
        s"{set.${renderKey(set)}/${renderKey(reference)}#${disambiguator.fold("0")(_.hashCode.toString)}"
    }
  }

  private[this] def extract(key: DIKey): Set[String] = {
    key match {
      case k: DIKey.TypeKey =>
        extract(k.tpe)

      case k: DIKey.IdKey[_] =>
        extract(k.tpe)

      case p: DIKey.ProxyElementKey =>
        extract(p.tpe) ++ extract(p.proxied)

      case s: DIKey.SetElementKey =>
        extract(s.tpe) ++ extract(s.reference)

      case r: DIKey.ResourceKey =>
        extract(r.tpe) ++ extract(r.tpe)

      case e: DIKey.EffectKey =>
        extract(e.tpe) ++ extract(e.tpe)
    }
  }

  private[this] def extract(key: SafeType): Set[String] = {
    RuntimeAPI.unpack(key.tag.ref match {
      case reference: LightTypeTagRef.AbstractReference =>
        reference
    }).map(_.ref.name)
  }

}

object KeyMinimizer {
  def apply(allKeys: Set[DIKey]): KeyMinimizer = new KeyMinimizer(allKeys)
}
