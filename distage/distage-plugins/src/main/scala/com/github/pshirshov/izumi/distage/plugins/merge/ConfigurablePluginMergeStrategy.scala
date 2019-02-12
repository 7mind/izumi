package com.github.pshirshov.izumi.distage.plugins.merge

import com.github.pshirshov.izumi.distage.model.definition.Binding.{ImplBinding, SetBinding}
import com.github.pshirshov.izumi.distage.model.definition.{Binding, BindingTag, Module}
import com.github.pshirshov.izumi.distage.model.exceptions.ModuleMergeException
import com.github.pshirshov.izumi.distage.model.reflection
import com.github.pshirshov.izumi.distage.plugins.merge.ConfigurablePluginMergeStrategy.PluginMergeConfig
import com.github.pshirshov.izumi.distage.plugins.{LoadedPlugins, PluginBase}
import distage.{DIKey, SafeType}

class ConfigurablePluginMergeStrategy(config: PluginMergeConfig) extends PluginMergeStrategy {
  override def merge(defs: Seq[PluginBase]): LoadedPlugins = {
    import com.github.pshirshov.izumi.fundamentals.collections.IzCollections._
    val allBindings = defs
      .map { plugin =>
        val filteredBindings = plugin.bindings.filterNot(isDisabled)
        PluginBase.make(filteredBindings)
      }
      .merge
      .bindings

    val resolved = allBindings
      .map(d => d.key -> d)
      .toMultimap
      .flatMap(resolve)

    LoadedPlugins(Module.make(resolved.toSet))
  }

  protected def resolve(kv: (reflection.universe.RuntimeDIUniverse.DIKey, Set[Binding])): Set[Binding] = {
    val (key, alternatives) = kv
    val name = keyClassName(key)
    val prefs = config.preferences.get(name)
      .orElse(config.preferences.get(name.split('.').last))

    prefs match {
      case None =>
        choose(key, alternatives)

      case Some(p) =>
        val matchingName = p.name match {
          case Some(n) =>
            alternatives.filter {
              alt =>
                implClassName(alt).forall(_.endsWith(n))
            }

          case None =>
            alternatives
        }

        p.tag match {
          case Some(tag) =>
            choose(key, matchingName.filter(_.tags.contains(BindingTag(tag))))
          case None =>
            choose(key, matchingName)
        }
    }

  }

  private def choose(key: reflection.universe.RuntimeDIUniverse.DIKey, alternatives: Set[Binding]): Set[Binding] = {
    if (alternatives.forall(_.isInstanceOf[SetBinding])) {
      alternatives
    } else {
      if (alternatives.size != 1) {
        throw new ModuleMergeException(s"Expected one alternative for $key got:\n${alternatives.mkString(" - ", "\n - ", "")}", Set(key))
      }

      Set(alternatives.head)
    }
  }

  protected def fullDisabledTagsExpr: BindingTag.Expressions.Expr = {
    import BindingTag.Expressions._
    config.disabledTags && !(Has(BindingTag.Untagged) && Has(BindingTag.TSet))
  }

  protected def isDisabled(binding: Binding): Boolean = {
    fullDisabledTagsExpr.evaluate(binding.tags) ||
      isDisabledName(keyClassName(binding.key), config.disabledKeyClassnames) ||
      implClassName(binding).exists(isDisabledName(_, config.disabledImplClassnames))
  }

  private def isDisabledName(t: String, disabledNames: Set[String]) = {
    disabledNames.contains(t) ||
      disabledNames.contains(t.split('.').last)
  }

  private def implClassName(binding: Binding): Option[String] = {
    binding match {
      case b: ImplBinding =>
        Some(typeName(b.implementation.implType))
      case _ =>
        None
    }
  }

  private def keyClassName(key: DIKey): String = {
    typeName(key.tpe)
  }


  private def typeName(k: SafeType): String = {
    k.tpe.typeSymbol.asClass.fullName
  }
}

object ConfigurablePluginMergeStrategy {

  case class BindingPreference(name: Option[String], tag: Option[String])

  case class PluginMergeConfig
  (
    disabledTags: BindingTag.Expressions.Expr
    , disabledKeyClassnames: Set[String] = Set.empty
    , disabledImplClassnames: Set[String] = Set.empty
    , preferences: Map[String, BindingPreference] = Map.empty
  )

}
