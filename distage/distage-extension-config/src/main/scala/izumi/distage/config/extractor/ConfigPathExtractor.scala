package izumi.distage.config.extractor

import com.typesafe.config.{Config, ConfigFactory}
import izumi.distage.config.extractor.ConfigPathExtractor.{ConfigPath, ExtractConfigPath1, ResolvedConfig}
import izumi.distage.model.definition.Binding.SingletonBinding
import izumi.distage.model.definition.BindingTag.ConfTag
import izumi.distage.model.definition.{Binding, ImplDef, Module, ModuleBase}
import izumi.distage.model.planning.PlanningHook
import izumi.distage.model.reflection.DIKey
import izumi.fundamentals.platform.language.SourceFilePosition

import scala.annotation.nowarn
import scala.jdk.CollectionConverters._

class ConfigPathExtractor extends PlanningHook {

  override def hookDefinition(defn: ModuleBase): ModuleBase = {
    val paths = defn.bindings.collect {
      case ExtractConfigPath1(configPath) =>
        configPath
    }

    val addResolvedConfigOp = resolvedConfigBindings(paths)

    defn ++ Module.make(Set(addResolvedConfigOp))
  }

  private def resolvedConfigBindings(paths: Set[ConfigPath]): SingletonBinding[DIKey] = {
    val resolvedConfig = ResolvedConfig(paths)
    val target = DIKey.get[ResolvedConfig]
    SingletonBinding(
      target,
      ImplDef.InstanceImpl(target.tpe, resolvedConfig),
      Set.empty,
      SourceFilePosition.unknown,
    )
  }

}

object ConfigPathExtractor {

  object ExtractConfigPath1 {
    def unapply(op: Binding): Option[ConfigPath] = {
      op.tags.collectFirst {
        case ConfTag(path) => ConfigPath(path)
      }
    }
  }

  final case class ResolvedConfig(requiredPaths: Set[ConfigPath]) {
    @nowarn("msg=Unused import")
    def minimized(source: Config): Config = {
      import scala.collection.compat._
      val paths = requiredPaths.map(_.toPath)

      ConfigFactory.parseMap {
        source
          .root().unwrapped().asScala
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
