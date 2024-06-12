package izumi.distage.framework

import dotty.tools.dotc.core.Contexts
import izumi.distage.framework.model.PlanCheckResult
import izumi.distage.model.definition.ModuleBase
import izumi.distage.plugins.StaticPluginLoader
import izumi.distage.plugins.load.LoadedPlugins
import izumi.fundamentals.platform.console.TrivialLogger
import izumi.fundamentals.platform.reflection.TrivialMacroLogger
import izumi.fundamentals.reflection.TypeUtil

import scala.quoted.runtime.impl.QuotesImpl
import scala.quoted.{Expr, Quotes, Type}

/**
  * This implicit performs a compile-time check for a given `distage`-based `App` when materialized.
  *
  * The reason to use an implicit macro for this, instead of a direct def-macro is because
  * implicit macros are more compositional – you may write new functions using the implicit without writing new macros,
  * by contrast macros can only be composed inside new macros – and this in turn requires separating them into different
  * `sbt` modules.
  *
  * @tparam AppMain The application to check, this should be a static object, most often a `main` object inherited from [[izumi.distage.roles.RoleAppMain]]
  * @tparam Cfg Additional configuration options for compile-time checker
  *
  * @see [[izumi.distage.framework.PlanCheck User API]]
  * @see [[izumi.distage.framework.PlanCheckConfig Configuration Options]]
  */
final case class PlanCheckMaterializer[AppMain <: CheckableApp, -Cfg <: PlanCheckConfig.Any](
  checkPassed: Boolean,
  checkedPlugins: Seq[ModuleBase],
  app: AppMain,
  roles: String,
  excludeActivations: String,
  config: String,
  checkConfig: Option[Boolean],
  printBindings: Option[Boolean],
  onlyWarn: Option[Boolean],
) {
  /** @throws izumi.distage.framework.model.exceptions.PlanCheckException on found issues */
  def assertAgainAtRuntime(): Unit = PlanCheck.runtime.assertApp(app, makeCfg)

  /** @return a list of issues, if any. Does not throw. */
  def checkAgainAtRuntime(): PlanCheckResult = PlanCheck.runtime.checkApp(app, makeCfg)

  def makeCfg: PlanCheckConfig.Any = new PlanCheckConfig(
    roles,
    excludeActivations,
    config,
    checkConfig = checkConfig.getOrElse(PlanCheck.defaultCheckConfig),
    printBindings = printBindings.getOrElse(PlanCheck.defaultPrintBindings),
    onlyWarn = onlyWarn.getOrElse(PlanCheck.defaultOnlyWarn),
  )
}

object PlanCheckMaterializer extends PlanCheckMaterializerCommon {

  inline implicit def materialize[
    AppMain <: CheckableApp,
    Roles <: String,
    ExcludeActivations <: String,
    Config <: String,
    CheckConfig <: Boolean,
    PrintBindings <: Boolean,
    OnlyWarn <: Boolean,
  ]: PlanCheckMaterializer[AppMain, PlanCheckConfig[Roles, ExcludeActivations, Config, CheckConfig, PrintBindings, OnlyWarn]] =
    ${ materializeImpl[AppMain, Roles, ExcludeActivations, Config, CheckConfig, PrintBindings, OnlyWarn] }

  def materializeImpl[
    AppMain <: CheckableApp: Type,
    Roles <: String: Type,
    Activations <: String: Type,
    Config <: String: Type,
    CheckConfig <: Boolean: Type,
    PrintBindings <: Boolean: Type,
    OnlyWarn <: Boolean: Type,
  ](using qctx: Quotes
  ): Expr[PlanCheckMaterializer[AppMain, PlanCheckConfig[Roles, Activations, Config, CheckConfig, PrintBindings, OnlyWarn]]] = {
    import qctx.reflect.*

    def getConstantType0[S](tpe: TypeRepr): S = {
      tpe match {
        case ConstantType(c) => c.value.asInstanceOf[S]
        case tpe =>
          report.errorAndAbort(
            s"""When materializing ${Type.show[PlanCheckMaterializer[AppMain, PlanCheckConfig[Roles, Activations, Config, CheckConfig, PrintBindings, OnlyWarn]]]},
               |Bad constant type: $tpe - Not a constant! Only constant literal types are supported!
             """.stripMargin
          )
      }
    }
    def getConstantType[S: Type]: S = {
      getConstantType0(TypeRepr.of[S].dealias)
    }
    def noneIfUnset[S: Type]: Option[S] = {
      val tpe = TypeRepr.of[S].dealias
      if (tpe =:= TypeRepr.of[PlanCheckConfig.Unset]) {
        None
      } else {
        Some(getConstantType0[S](tpe))
      }
    }

    val roleAppMain = Ref(TypeRepr.of[AppMain].termSymbol).asExprOf[AppMain]
    val roles = getConstantType[Roles]
    val activations = getConstantType[Activations]
    val config = getConstantType[Config]
    val checkConfig = noneIfUnset[CheckConfig]
    val printBindings = noneIfUnset[PrintBindings]
    val onlyWarn = noneIfUnset[OnlyWarn]
    val warn = onlyWarn.getOrElse(PlanCheck.defaultOnlyWarn)

    val maybeMain = instantiateObject[AppMain]

    val logger = TrivialMacroLogger.make[this.type](DebugProperties.`izumi.debug.macro.distage.plancheck`.name)

    val (maybeMessage, checkedLoadedPlugins) = {
      doCheckApp(roles, activations, config, checkConfig, printBindings, warn, maybeMain, logger)
    }
    val checkPassed = maybeMessage match {
      case None => true
      case Some(message) =>
        val i = 10240 * 6
        val shortMessage = if (message.length > i) {
          message.take(i) + s"[Scala 3 limitation, error too large, ${message.drop(i).length} characters omitted]"
        } else {
          message
        }
        if (warn) {
          implicit val ctx: Contexts.Context = qctx.asInstanceOf[QuotesImpl].ctx
          val fatalWarnings =
            ctx.settings.Wconf.value.contains("any:error") ||
            ctx.settings.XfatalWarnings.value
          if (fatalWarnings) {
            qctx.reflect.report.info(shortMessage)
          } else {
            qctx.reflect.report.warning(shortMessage)
          }
          false
        } else {
          qctx.reflect.report.errorAndAbort(shortMessage)
        }
    }

    // filter out anonymous classes that can't be referred in code
    // & retain only those that are suitable for being loaded by PluginLoader (objects / zero-arg classes) -
    // and that can be easily instantiated with `new`
    val referencablePlugins = filterReferencablePlugins(checkedLoadedPlugins)

    // NOTE: We haven't checked yet if we _have_ to splice `new` for to trigger recompilation for Scala 3 - on Intellij or in general,
    // still, for now we use the same strategy as in Scala 2 - we splice `new` expressions.
    val pluginsList: Expr[List[ModuleBase]] = Expr.ofList(StaticPluginLoader.instantiatePluginsInCode[ModuleBase](referencablePlugins))

    '{
      val referencedPlugins = ${ pluginsList }
      new PlanCheckMaterializer[AppMain, PlanCheckConfig[Roles, Activations, Config, CheckConfig, PrintBindings, OnlyWarn]](
        checkPassed = ${ Expr(checkPassed) },
        checkedPlugins = referencedPlugins,
        app = ${ roleAppMain },
        roles = ${ Expr(roles) },
        excludeActivations = ${ Expr(activations) },
        config = ${ Expr(config) },
        checkConfig = ${ Expr(checkConfig) },
        printBindings = ${ Expr(printBindings) },
        onlyWarn = ${ Expr(onlyWarn) },
      )
    }
  }

  private def instantiateObject[T: Type](using qctx: Quotes): T = {
    import qctx.reflect.*
    val symbol = TypeRepr.of[T].typeSymbol
    val className = symbol.fullName.replace("$.", "$") // hack around nested objects
    val clazz = Class.forName(className)
    TypeUtil.instantiateObject[T](clazz)
  }

}
