package com.github.pshirshov.izumi.distage.config

import com.github.pshirshov.izumi.distage.model.exceptions.DIException
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.ImportDependency
import com.github.pshirshov.izumi.distage.model.plan.{ExecutableOp, FinalPlan, FinalPlanImmutableImpl}
import com.github.pshirshov.izumi.distage.model.planning.PlanningHook
import com.github.pshirshov.izumi.distage.model.reflection.universe
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import com.typesafe.config.Config

import scala.util.Try
import scala.util.control.NonFatal


final case class AppConfig(config: Config)

final case class ConfiguredPlan(configured: FinalPlan)

trait ConfigResolver {
  def resolve(plan: FinalPlan): ConfiguredPlan
}

/**
  * This annotation tells config resolution mechanism to use all the context information
  * to resolve config entry, namely:
  * - Config class name
  * - Binding class name for the binding requiring the config value
  * - Binding name for the binding requiring the config value
  *
  * So, altogether config path structure for autoconf entry would be
  *
  * {binding_type|fq_binding_type}.{config_type|fq_config_type}.{binding_name?|%}
  */
final class AutoConf() extends scala.annotation.StaticAnnotation

/**
  * This annotation is the same as [[AutoConf]] one but does not use binding names
  */
final class Conf(val name: String) extends scala.annotation.StaticAnnotation


private case class ConfigImport(id: AbstractConfId, imp: ImportDependency)

private object ConfigImport {
  def unapply(op: ExecutableOp): Option[ConfigImport] = {
    op match {
      case i: ImportDependency =>
        unapply(op.target).map(id => ConfigImport(id, i))
      case _ =>
        None
    }
  }

  private def unapply(arg: DIKey): Option[AbstractConfId] = {
    arg match {
      case k: DIKey.IdKey[_] =>
        k.id match {
          case id: AbstractConfId =>
            Some(id)
          case _ =>
            None
        }

      case _ =>
        None
    }
  }
}

class ConfigProvider(config: AppConfig, reader: ConfigInstanceReader) extends PlanningHook {
  override def hookFinal(plan: FinalPlan): FinalPlan = {


    val updatedSteps = plan.steps
      .map {
        case ConfigImport(ci) =>
          try {
            val requirement = toRequirement(plan.index, ci)
            TranslationResult.Success(translate(requirement))
          } catch {
            case NonFatal(t) =>
              TranslationResult.Failure(t)
          }

        case s =>
          TranslationResult.Success(s)
      }

    val errors = updatedSteps.collect({ case TranslationResult.Failure(op) => op })

    if (errors.nonEmpty) {
      throw new DIException(s"Cannot resolve config:\n${errors.map(_.getMessage).mkString("\n")}", errors.head)
    }

    val ops = updatedSteps.collect({ case TranslationResult.Success(op) => op })
    val newPlan = FinalPlanImmutableImpl(plan.definition, ops)
    newPlan
  }


  private def translate(step: RequiredConfigEntry): ExecutableOp = {
    val results = step.paths.map(p => Try(config.config.getConfig(p.toPath)))
    val loaded = results.collect({ case scala.util.Success(value) => value })

    if (loaded.isEmpty) {
      val tried = step.paths.mkString("{", "|", "}")
      throw new DIException(s"Cannot load config value for ${step.target} from: $tried", null)
    }

    val section = loaded.head
    val product = reader.read(section, step.targetClass)
    ExecutableOp.WiringOp.ReferenceInstance(step.target, Wiring.UnaryWiring.Instance(step.target.symbol, product))
  }

  implicit class TypeExt(t: TypeFull) {
    def name: String = t.tpe.typeSymbol.asClass.fullName
  }

  case class DepType(fqName: Seq[String], qualifier: Seq[String]) {
    def name: Seq[String] = Seq(fqName.last)
  }

  case class DepUsage(fqName: Seq[String], qualifier: Seq[String]) {
    def name: Seq[String] = Seq(fqName.last)
  }

  case class DependencyContext(dep: DepType, usage: DepUsage)

  private def toRequirement(index: Map[RuntimeDIUniverse.DIKey, ExecutableOp], op: ConfigImport): RequiredConfigEntry = {
    val dc = DependencyContext(structInfo(index, op), usageInfo(op))

    val paths = Seq(
      ConfigPath(dc.usage.fqName ++ dc.usage.qualifier ++ dc.dep.fqName ++ dc.dep.qualifier)
      , ConfigPath(dc.usage.fqName ++ dc.usage.qualifier ++ dc.dep.name ++ dc.dep.qualifier)
      , ConfigPath(dc.usage.name ++ dc.usage.qualifier ++ dc.dep.fqName ++ dc.dep.qualifier)
      , ConfigPath(dc.usage.name ++ dc.usage.qualifier ++ dc.dep.name ++ dc.dep.qualifier)
    )

    //println(paths.map(_.toPath))
    val runtimeClass = mirror.runtimeClass(op.imp.target.symbol.tpe.erasure)
    RequiredConfigEntry(paths, runtimeClass, op.imp.target)
  }


  private def structInfo(index: Map[universe.RuntimeDIUniverse.DIKey, ExecutableOp], op: ConfigImport) = {
    //println(op.imp.references.map(index.apply))

    val structFqName = op.imp.target.symbol.name
    val structFqParts = structFqName.split('.').toSeq
    DepType(structFqParts, Seq("%")) // TODO: extract para name from index
  }

  private def usageInfo(op: ConfigImport) = {
    val usageKeyFqName = op.id match {
      case id: AutoConfId =>
        id.context.symbol.name
      case id: ConfId =>
        id.context
    }

    val usageKeyParts: Seq[String] = usageKeyFqName.split('.').toSeq

    val usageKeyQualifier = op.id match {
      case id: AutoConfId =>
        id.context match {
          case k: DIKey.IdKey[_] =>
            Some(k.id.toString) // TODO: not nice, better to use IdContract

          case _ =>
            None
        }

      case _ =>
        None
    }
    val usageQualifier = Seq(usageKeyQualifier.getOrElse("%"))
    DepUsage(usageKeyParts, usageQualifier)
  }
}




