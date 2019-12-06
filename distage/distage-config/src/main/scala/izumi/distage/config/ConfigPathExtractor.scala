package izumi.distage.config

import com.typesafe.config.{Config, ConfigFactory}
import izumi.distage.config.ConfigPathExtractor._
import izumi.distage.config.model.AppConfig
import izumi.distage.model.definition.BindingTag.ConfTag
import izumi.distage.model.plan.operations.OperationOrigin
import izumi.distage.model.plan.{ExecutableOp, SemiPlan}
import izumi.distage.model.planning.PlanningHook
import izumi.distage.model.reflection.universe.RuntimeDIUniverse.DIKey
import izumi.distage.model.reflection.universe.RuntimeDIUniverse.Wiring.SingletonWiring.Instance

import scala.jdk.CollectionConverters._

class ConfigPathExtractor
(
  config: AppConfig,
) extends PlanningHook {

  override def phase20Customization(plan: SemiPlan): SemiPlan = {
    val paths = plan.steps.collect {
      case ExtractConfigPath(configPath) => configPath
    }.toSet

    val addResolvedConfigOp = resolvedConfigOp(config, paths)

    SemiPlan(plan.steps :+ addResolvedConfigOp, plan.gcMode)
  }

  private def resolvedConfigOp(config: AppConfig, paths: Set[ConfigPath]): ExecutableOp.WiringOp.UseInstance = {
    val resolvedConfig = ResolvedConfig(config, paths)
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

  final case class ResolvedConfig(source: AppConfig, requiredPaths: Set[ConfigPath]) {
    def minimized(): Config = {
      val paths = requiredPaths.map(_.toPath)

      ConfigFactory.parseMap {
        source.config.root().unwrapped().asScala
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
    def apply(path: String): ConfigPath = new ConfigPath(path.split('.'))
  }

}
