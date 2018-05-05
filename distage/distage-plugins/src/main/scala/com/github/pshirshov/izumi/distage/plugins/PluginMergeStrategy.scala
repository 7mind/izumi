package com.github.pshirshov.izumi.distage.plugins

import com.github.pshirshov.izumi.distage.model.definition.Binding.ImplBinding
import com.github.pshirshov.izumi.distage.model.definition.{Binding, ImplDef, TrivialModuleDef}
import com.github.pshirshov.izumi.distage.model.exceptions.DIException
import com.github.pshirshov.izumi.distage.model.reflection


trait PluginMergeStrategy[T <: LoadedPlugins] {
  def merge[D <: PluginDef](defs: Seq[D]): T
}

object SimplePluginMergeStrategy extends PluginMergeStrategy[LoadedPlugins] {
  override def merge[D <: PluginDef](defs: Seq[D]): LoadedPlugins = {
    val merged = defs.merge()
    JustLoadedPlugins(merged)
  }
}

case class BindingPreference(name: Option[String], tag: Option[String])

case class PluginMergeConfig
(
  disabledImplementations: Set[String]
  , disabledKeys: Set[String]
  , disabledTags: Set[String]
  , preferences: Map[String, BindingPreference]
)

class ConfigurablePluginMergeStrategy(config: PluginMergeConfig) extends PluginMergeStrategy[LoadedPlugins] {
  override def merge[D <: PluginDef](defs: Seq[D]): LoadedPlugins = {
    import com.github.pshirshov.izumi.fundamentals.collections.IzCollections._
    val allBindings = defs.merge().bindings

    val resolved = allBindings
      .filterNot(isDisabled)
      .map(d => d.key -> d)
      .toMultimap
      .map(resolve)

    JustLoadedPlugins(TrivialModuleDef(resolved.toSet))
  }

  protected def tagsOf(binding: Binding): Set[String] = {
    // TODO
    Set.empty
  }

  protected def resolve(kv: (reflection.universe.RuntimeDIUniverse.DIKey, Set[Binding])): Binding = {
    val (key, alternatives) = kv
    val name = key.symbol.tpe.typeSymbol.asClass.fullName
    val prefs = config.preferences.get(name)
      .orElse(config.preferences.get(name.split('.').last))

    prefs match {
      case None =>
        oneAlternative(alternatives)

      case Some(p) =>
        val matchingName = p.name match {
          case Some(n) =>
            alternatives.filter {
              alt =>
                implName(alt).forall(_.endsWith(n))
            }

          case None =>
            alternatives
        }

        p.tag match {
          case Some(tag) =>
            oneAlternative(matchingName.filter(b => tagsOf(b).contains(tag)))
          case None =>
            oneAlternative(matchingName)
        }
    }

  }

  private def oneAlternative(alternatives: Set[Binding]) = {
    if (alternatives.size != 1) {
      throw new DIException(s"Expected one alternative, got $alternatives", null)
    }
    alternatives.head
  }

  protected def isDisabled(binding: Binding): Boolean = {
    val maybeImplName = implName(binding).filter {
      name =>
        config.disabledImplementations.contains(name) ||
          config.disabledImplementations.contains(name.split('.').last)
    }


    val hasDisabledTags = tagsOf(binding).intersect(config.disabledTags).nonEmpty

    val hasDisabledName = maybeImplName.isDefined
    val hasDisabledImplName = {
      val keyName = binding.key.symbol.tpe.typeSymbol.asClass.fullName

      config.disabledImplementations.contains(keyName) ||
        config.disabledImplementations.contains(keyName.split('.').last)
    }

    hasDisabledName || hasDisabledTags || hasDisabledImplName
  }

  private def implName(binding: Binding): Option[String] = {
    (binding match {
      case b: ImplBinding =>
        Option(b.implementation)
      case _ =>
        None
    }).flatMap {
      case i: ImplDef.WithImplType =>
        Option(i.implType.tpe.typeSymbol.asClass.fullName)
      case _ =>
        None
    }
  }
}
