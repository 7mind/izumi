package izumi.distage.config

import com.typesafe.config.Config
import izumi.distage.config.codec.ConfigReader
import izumi.distage.config.model.{AppConfig, ConfPathId}
import izumi.distage.model.definition.Binding.SingletonBinding
import izumi.distage.model.definition.ImplDef.ReferenceImpl
import izumi.distage.model.definition.dsl.AbstractBindingDefDSL.MultipleRef
import izumi.distage.model.definition.dsl.ModuleDefDSL.{MakeDSL, MultipleDSL, MultipleNamedDSL}
import izumi.distage.model.definition.{BootstrapModuleDef, ModuleBase, ModuleDef}
import izumi.distage.model.planning.PlanningHook
import izumi.distage.model.reflection.universe.RuntimeDIUniverse.DIKey
import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.fundamentals.reflection.Tags.Tag

// FIXME: AppConfigModule: non-bootstrap def?
class AppConfigModule(appConfig: AppConfig) extends BootstrapModuleDef {
  def this(config: Config) = this(AppConfig(config))

  make[AppConfig].fromValue(appConfig)
}

object AppConfigModule {
  def apply(appConfig: AppConfig): AppConfigModule = new AppConfigModule(appConfig)
  def apply(config: Config): AppConfigModule = new AppConfigModule(config)
}

class ConfigPathExtractorModule extends BootstrapModuleDef {
  many[PlanningHook]
    .add[ConfigPathExtractor]
}

trait ConfigModuleDef extends ModuleDef {
  // FIXME: use annotations/attachments/metadata instead of Ids here, also resolve Role companion object ???
  final def makeConfig[T: Tag: ConfigReader](path: String)(implicit pos: CodePositionMaterializer): MultipleDSL[T] = {
    val id = ConfPathId(path)
    make[T].named(id).from {
      config: AppConfig =>
        ConfigReader[T].apply(config.config.getValue(path)).get
    }
    val key = DIKey.get[T]
    val ref = _registered(new MultipleRef(SingletonBinding(key, ReferenceImpl(key.tpe, DIKey.get[T].named(id), weak = false), Set.empty, pos.get.position), pos.get.position))
    new MultipleDSL(ref, key)
  }
  // FIXME: use annotations/attachments/metadata instead of Ids here, also resolve Role companion object ???
  final def makeConfigNamed[T: Tag: ConfigReader](path: String)(implicit pos: CodePositionMaterializer): MultipleNamedDSL[T] = {
    makeConfig[T](path).named(path)
  }

  implicit final class FromConfig[T](private val dsl: MakeDSL[T]) {
    def fromConfig(path: String)(implicit dec: ConfigReader[T], tag: Tag[T], pos: CodePositionMaterializer): Unit = {
      // FIXME: use annotations/attachments/metadata instead of Ids here, also resolve Role companion object ???
      val id = ConfPathId(path)
      dsl.named(id).from {
        config: AppConfig =>
          ConfigReader[T].apply(config.config.getValue(path)).get
      }
      hackyMakeReferenceImpl(DIKey.get[T], DIKey.get[T].named(id))
    }
    def fromConfigNamed(path: String)(implicit dec: ConfigReader[T], tag: Tag[T], pos: CodePositionMaterializer): Unit = {
      val id = ConfPathId(path)
      dsl.named(id).from {
        config: AppConfig =>
          ConfigReader[T].apply(config.config.getValue(path)).get
      }
      hackyMakeReferenceImpl(DIKey.get[T].named(path), DIKey.get[T].named(id))
    }
  }

  // FIXME: use annotations/attachments/metadata instead of Ids here, also resolve Role companion object ???
  // FIXME: `.using` usable with non-String names
  private[this] def hackyMakeReferenceImpl(key: DIKey, ref: DIKey)(implicit pos: CodePositionMaterializer): Unit = {
    include(ModuleBase.make(Set(
      SingletonBinding(key, ReferenceImpl(ref.tpe, ref, weak = false), Set.empty, pos.get.position)
    )))
  }
}
