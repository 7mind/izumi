package com.github.pshirshov.izumi.distage.config

import com.github.pshirshov.izumi.distage.config.TranslationResult.TranslationFailure
import com.github.pshirshov.izumi.distage.config.annotations._
import com.github.pshirshov.izumi.distage.config.model.AppConfig
import com.github.pshirshov.izumi.distage.config.model.exceptions.ConfigTranslationException
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.ImportDependency
import com.github.pshirshov.izumi.distage.model.plan.{ExecutableOp, SemiPlan}
import com.github.pshirshov.izumi.distage.model.planning.PlanningHook
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import com.github.pshirshov.izumi.fundamentals.typesafe.config.RuntimeConfigReader
import com.typesafe.config.{ConfigException, ConfigObject, ConfigValue}

import scala.util.Try
import scala.util.control.NonFatal


class ConfigProvider(config: AppConfig, reader: RuntimeConfigReader, injectorConfig: ConfigInjectorConfig)
  extends PlanningHook {

  import ConfigProvider._

  override def phase20Customization(plan: SemiPlan): SemiPlan = {
    val updatedSteps = plan.steps
      .map {
        case ConfigImport(ci) =>
          try {
            val requirement = toRequirement(ci)
            translate(ci, requirement)
          } catch {
            case NonFatal(t) =>
              TranslationResult.Failure(ci.imp, config.config.origin, t)
          }

        case s =>
          TranslationResult.Success(s)
      }

    val errors = updatedSteps.collect({ case t: TranslationFailure => t })

    if (errors.nonEmpty) {
      // TODO: instead of throwing exception we may just print a warning and leave import in place. It would fail on provisioning anyway
      throw new ConfigTranslationException(s"Cannot resolve config due to errors:\n - ${errors.mkString("\n - ")}", errors)
    }

    val ops = updatedSteps.collect({ case TranslationResult.Success(op) => op })
    val newPlan = SemiPlan(plan.definition, ops)
    newPlan
  }

  private def translate(ci: ConfigImport, step: RequiredConfigEntry): TranslationResult = {
    val op = ci.imp
    val results = step.paths.map {
      p =>
        (p, Try(config.config.getValue(p.toPath)))
    }

    val loaded = results.collect({ case (path, scala.util.Success(value)) => (path, value) })

    loaded.headOption match {
      case Some((loadedPath, loadedValue)) =>
        try {
          val loaded = toProduct(step, loadedPath, loadedValue)
          val product = injectorConfig.transformer.transform.lift((ci, loaded)).getOrElse(loaded)
          TranslationResult.Success(
            ExecutableOp.WiringOp.ReferenceInstance(
              step.target
              , Wiring.UnaryWiring.Instance(step.target.tpe, product), op.origin
            )
          )
        } catch {
          case NonFatal(t) =>
            TranslationResult.ExtractionFailure(op, step.targetType, loadedPath.toPath, loadedValue, config.config.origin, t)
        }

      case None =>
        val failures = results.collect({ case (path, scala.util.Failure(f)) => (path, f) })
        TranslationResult.MissingConfigValue(op, failures, config.config.origin)
    }
  }

  private def toProduct(step: RequiredConfigEntry, loadedPath: ConfigPath, loadedValue: ConfigValue): Any = {
    loadedValue match {
      case obj: ConfigObject =>
        reader.readConfigAsCaseClass(obj.toConfig, step.targetType)
      case o if injectorConfig.enableScalars =>
        reader.readValue(o, step.targetType)
      case o =>
        throw new ConfigException.WrongType(o.origin(), loadedPath.toPath, "Object", o.valueType().toString)
    }
  }

  implicit class TypeExt(t: SafeType) {
    def name: String = t.tpe.typeSymbol.asClass.fullName
  }

  case class DepType(fqName: Seq[String], qualifier: Seq[String]) {
    def name: Seq[String] = Seq(fqName.last)
  }

  case class DepUsage(fqName: Seq[String], qualifier: Seq[String]) {
    def name: Seq[String] = Seq(fqName.last)
  }

  case class DependencyContext(dep: DepType, usage: DepUsage)

  private def toRequirement(op: ConfigImport): RequiredConfigEntry = {
    val paths = op.id match {
      case p: ConfPathId =>
        Seq(
          ConfigPath(p.pathOverride.split('.'))
        )

      case _: AutomaticConfId =>
        toRequirementAuto(op)
    }

    RequiredConfigEntry(paths, op.imp.target.tpe, op.imp.target)

  }

  private def toRequirementAuto(op: ConfigImport): Seq[ConfigPath] = {
    val dc = DependencyContext(structInfo(op), usageInfo(op))

    Seq(
      ConfigPath(dc.usage.fqName ++ dc.usage.qualifier ++ dc.dep.fqName ++ dc.dep.qualifier)
      , ConfigPath(dc.usage.fqName ++ dc.usage.qualifier ++ dc.dep.name ++ dc.dep.qualifier)
      , ConfigPath(dc.usage.name ++ dc.usage.qualifier ++ dc.dep.fqName ++ dc.dep.qualifier)
      , ConfigPath(dc.usage.name ++ dc.usage.qualifier ++ dc.dep.name ++ dc.dep.qualifier)

      , ConfigPath(dc.usage.fqName ++ dc.usage.qualifier ++ dc.dep.fqName)
      , ConfigPath(dc.usage.fqName ++ dc.usage.qualifier ++ dc.dep.name)
      , ConfigPath(dc.usage.name ++ dc.usage.qualifier ++ dc.dep.fqName)
      , ConfigPath(dc.usage.name ++ dc.usage.qualifier ++ dc.dep.name)
    ).distinct
  }

  private def structInfo(op: ConfigImport) = {
    val qualifier = op.id match {
      case id: AutoConfId =>
        id.parameter.name
      case id: ConfId =>
        id.parameter.name
      case _ =>
        throw new IllegalArgumentException(s"Unexpected op: $op")
    }


    val structFqName = op.imp.target.tpe.name
    val structFqParts = structFqName.split('.').toSeq
    DepType(structFqParts, Seq(qualifier))
  }

  private def usageInfo(op: ConfigImport) = {
    /* we may get set type the following way:

     case id: AutomaticConfId =>
      id.binding match {
        case b: RuntimeDIUniverse.DIKey.SetElementKey =>
          b.set.tpe.tpe.typeArgs.head.typeSymbol.name.decodedName.toString

     Though in that case we need to disambiguate set members somehow
     */

    val usageKeyFqName = op.id match {
      case id: AutoConfId =>
        id.binding.tpe.name
      case id: ConfId =>
        id.nameOverride
      case _ =>
        throw new IllegalArgumentException(s"Unexpected op: $op")
    }

    val usageKeyParts: Seq[String] = usageKeyFqName.split('.').toSeq

    val usageKeyQualifier = op.id match {
      case id: AutoConfId =>
        id.binding match {
          case k: DIKey.IdKey[_] =>
            Some(k.idContract.repr(k.id))

          case _ =>
            None
        }

      case _ =>
        None
    }
    val usageQualifier = usageKeyQualifier.toSeq
    DepUsage(usageKeyParts, usageQualifier)
  }

}

object ConfigProvider {

  private case class RequiredConfigEntry(paths: Seq[ConfigPath], targetType: SafeType, target: DIKey) {
    override def toString: String = {
      val allPaths = paths.map(_.toPath).mkString("\n  ")

      s"""type: $targetType, target: $target
         |$allPaths""".stripMargin
    }
  }

  case class ConfigImport(id: AbstractConfId, imp: ImportDependency)

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

}
