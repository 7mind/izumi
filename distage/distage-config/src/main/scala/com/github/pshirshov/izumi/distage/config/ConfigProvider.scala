package com.github.pshirshov.izumi.distage.config

import com.github.pshirshov.izumi.distage.config.annotations._
import com.github.pshirshov.izumi.distage.config.codec.RuntimeConfigReader
import com.github.pshirshov.izumi.distage.config.model.AppConfig
import com.github.pshirshov.izumi.distage.model.exceptions.DIException
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.ImportDependency
import com.github.pshirshov.izumi.distage.model.plan.{ExecutableOp, FinalPlan, FinalPlanImmutableImpl}
import com.github.pshirshov.izumi.distage.model.planning.PlanningHook
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import com.github.pshirshov.izumi.fundamentals.platform.exceptions.IzThrowable._

import scala.util.Try
import scala.util.control.NonFatal

class ConfigProvider(config: AppConfig, reader: RuntimeConfigReader) extends PlanningHook {

  import ConfigProvider._

  override def hookFinal(plan: FinalPlan): FinalPlan = {


    val updatedSteps = plan.steps
      .map {
        case ConfigImport(ci) =>
          try {
            val requirement = toRequirement(ci)
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
      throw new DIException(s"Cannot resolve config:\n${errors.map(_.stackTrace).mkString("\n")}", errors.head)
    }

    val ops = updatedSteps.collect({ case TranslationResult.Success(op) => op })
    val newPlan = FinalPlanImmutableImpl(plan.definition, ops)
    newPlan
  }

  private def translate(step: RequiredConfigEntry): ExecutableOp = {
    val results = step.paths.map(p => Try((p.toPath, config.config.getConfig(p.toPath))))
    val loaded = results.collect({ case scala.util.Success(value) => value })

    if (loaded.isEmpty) {
      val tried = step.paths.mkString("{", "|", "}")
      throw new DIException(s"Cannot find config value for ${step.target} from paths: $tried", null)
    }

    val section = loaded.head
    try {
      val product = reader.readConfig(section._2, step.targetType)
      ExecutableOp.WiringOp.ReferenceInstance(step.target, Wiring.UnaryWiring.Instance(step.target.tpe, product))
    } catch {
      case NonFatal(t) =>
        throw new DIException(s"Cannot read ${step.targetType} out of ${section._1} ==> ${section._2}", t)
    }
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

  private case class RequiredConfigEntry(paths: Seq[ConfigPath], targetType: TypeFull, target: DIKey) {
    override def toString: String = {
      val allPaths = paths.map(_.toPath).mkString("\n  ")

      s"""type: $targetType, target: $target
         |$allPaths""".stripMargin
    }
  }


  private case class ConfigPath(parts: Seq[String]) {
    def toPath: String = parts.mkString(".")

    override def toString: String = s"cfg:$toPath"
  }

  private sealed trait TranslationResult

  private object TranslationResult {

    final case class Success(op: ExecutableOp) extends TranslationResult

    final case class Failure(f: Throwable) extends TranslationResult

  }

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

}
