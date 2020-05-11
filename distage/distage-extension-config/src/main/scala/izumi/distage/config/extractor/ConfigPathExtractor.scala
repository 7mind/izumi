package izumi.distage.config.extractor

import com.github.ghik.silencer.silent
import com.typesafe.config.{Config, ConfigFactory}
import izumi.distage.config.extractor.ConfigPathExtractor.{ConfigPath, ExtractConfigPath, ResolvedConfig}
import izumi.distage.model.definition.BindingTag.ConfTag
import izumi.distage.model.plan.operations.OperationOrigin
import izumi.distage.model.plan.{ExecutableOp, SemiPlan}
import izumi.distage.model.planning.PlanningHook
import izumi.distage.model.reflection.DIKey
import izumi.distage.model.plan.Wiring.SingletonWiring.Instance

import scala.jdk.CollectionConverters._

class ConfigPathExtractor extends PlanningHook {

  override def phase20Customization(plan: SemiPlan): SemiPlan = {
    val paths = plan.steps.collect {
      case ExtractConfigPath(configPath) => configPath
    }.toSet

    val addResolvedConfigOp = resolvedConfigOp(paths)

    SemiPlan(plan.steps :+ addResolvedConfigOp, plan.gcMode)
  }

  private def resolvedConfigOp(paths: Set[ConfigPath]): ExecutableOp.WiringOp.UseInstance = {
    val resolvedConfig = ResolvedConfig(paths)
    val target = DIKey.get[ResolvedConfig]
    ExecutableOp.WiringOp.UseInstance(
      target = target,
      wiring = Instance(target.tpe, resolvedConfig),
      origin = OperationOrigin.Unknown,
    )
  }

}

object ConfigPathExtractor {

  object ExtractConfigPath {
    def unapply(op: ExecutableOp): Option[ConfigPath] = {
      op.origin match {
        case defined: OperationOrigin.Defined =>
          defined.binding.tags.collectFirst {
            case ConfTag(path) => ConfigPath(path)
          }
        case _ =>
          None
      }
    }
  }

  final case class ResolvedConfig(requiredPaths: Set[ConfigPath]) {
    @silent("Unused import")
    def minimized(source: Config): Config = {
      import scala.collection.compat._
      val paths = requiredPaths.map(_.toPath)

      ConfigFactory.parseMap {
        source.root().unwrapped().asScala
          .view
          .filterKeys(key => paths.exists(_.startsWith(key)))
          .toMap
          .asJava
      }
    }
  }

  final case class ConfigPath(parts: Seq[String]) {
    def toPath: String = parts.mkString(".")
    override def toString: String = s"cfg:$toPath"
  }
  object ConfigPath {
    def apply(path: String): ConfigPath = new ConfigPath(path.split('.').toIndexedSeq)
  }

}
