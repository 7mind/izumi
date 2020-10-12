package izumi.distage.framework

import izumi.distage.framework.PlanCheck.checkRoleApp
import izumi.distage.plugins.PluginBase
import izumi.distage.plugins.StaticPluginLoader.StaticPluginLoaderMacro
import izumi.distage.plugins.load.LoadedPlugins
import izumi.distage.roles.PlanHolder
import izumi.fundamentals.platform.exceptions.IzThrowable.toRichThrowable
import izumi.fundamentals.platform.language.{LiteralCompat, unused}
import izumi.fundamentals.reflection.{TrivialMacroLogger, TypeUtil}

import scala.language.experimental.macros
import scala.language.implicitConversions
import scala.reflect.api.Universe
import scala.reflect.macros.{blackbox, whitebox}

object PlanCheckMacro {

  final case class PerformPlanCheck[RoleAppMain <: PlanHolder, Roles <: String, Activations <: String, Config <: String, CheckConfig <: Boolean, PrintPlan <: Boolean](
    roleAppMain: RoleAppMain,
    roles: Roles,
    activations: Activations,
    config: Config,
    checkConfig: Option[CheckConfig],
    printPlan: Option[PrintPlan],
    checkedPlugins: Seq[PluginBase],
  ) {
    def run(): LoadedPlugins =
      checkRoleApp(
        roleAppMain = roleAppMain,
        roles = roles,
        activations = activations,
        config = config,
        checkConfig = checkConfig.getOrElse(PlanCheck.defaultCheckConfig),
        printPlan = printPlan.getOrElse(PlanCheck.defaultPrintPlan),
      )
  }
  object PerformPlanCheck {
    implicit def performCompileTimeCheck[
      RoleAppMain <: PlanHolder,
      Roles <: String,
      Activations <: String,
      Config <: String,
      CheckConfig <: Boolean,
      PrintPlan <: Boolean,
    ]: PerformPlanCheck[RoleAppMain, Roles, Activations, Config, CheckConfig, PrintPlan] =
      macro Materialize.impl[RoleAppMain, Roles, Activations, Config, CheckConfig, PrintPlan]
  }

  object Materialize {
    def impl[
      RoleAppMain <: PlanHolder: c.WeakTypeTag,
      Roles <: String: c.WeakTypeTag,
      Activations <: String: c.WeakTypeTag,
      Config <: String: c.WeakTypeTag,
      CheckConfig <: Boolean: c.WeakTypeTag,
      PrintPlan <: Boolean: c.WeakTypeTag,
    ](c: blackbox.Context
    ): c.Expr[PerformPlanCheck[RoleAppMain, Roles, Activations, Config, CheckConfig, PrintPlan]] = {
      import c.universe._

      def getConstantType[S: c.WeakTypeTag]: S = {
        weakTypeOf[S].dealias match {
          case ConstantType(Constant(s)) => s.asInstanceOf[S]
          case tpe =>
            c.abort(
              c.enclosingPosition,
              s"""When materializing ${weakTypeOf[PerformPlanCheck[RoleAppMain, Roles, Activations, Config, CheckConfig, PrintPlan]]},
                 |Bad constant type: $tpe - Not a constant! Only constant literal types are supported!
               """.stripMargin,
            )
        }
      }
      def noneIfNothing[S: c.WeakTypeTag](action: => S): Option[S] = if (weakTypeOf[S] ne definitions.NothingTpe) Some(action) else None

      val roleAppMain = c.Expr[RoleAppMain](q"${weakTypeOf[RoleAppMain].asInstanceOf[SingleTypeApi].sym.asTerm}")
      val roles = getConstantType[Roles]
      val activations = getConstantType[Activations]
      val config = getConstantType[Config]
      val checkConfig = noneIfNothing(getConstantType[CheckConfig])
      val printPlan = noneIfNothing(getConstantType[PrintPlan])

      val maybeMain = instantiateObject[RoleAppMain](c.universe)

      val logger = TrivialMacroLogger.make[this.type](c, DebugProperties.`distage.plancheck.debug`.name)
      val checkedLoadedPlugins =
        try {
          PlanCheck.checkRoleApp(
            maybeMain,
            roles,
            activations,
            config,
            checkConfig.getOrElse(PlanCheck.defaultCheckConfig),
            printPlan.getOrElse(PlanCheck.defaultPrintPlan),
            logger = logger,
          )
        } catch {
          case t: Throwable =>
            c.abort(c.enclosingPosition, t.stackTrace)
        }

      // filter out anonymous classes that can't be referred in code
      // & retain only those that are suitable for being loaded by PluginLoader (objects / zero-arg classes) -
      // and that can be easily instantiated with `new`
      val referencablePlugins = checkedLoadedPlugins
        .allRaw
        .filterNot(_.getClass.isAnonymousClass)
        .filter(p => TypeUtil.isObject(p.getClass).isDefined || TypeUtil.isZeroArgClass(p.getClass).isDefined)

      // We _have_ to call `new` to cause Intellij's incremental compiler to recompile users,
      // we can't just splice a type reference or create an expression referencing the type for it to pickup,
      // Zinc does recompile in these cases, but for Intellij `new` is required
      val pluginsList: List[Tree] = StaticPluginLoaderMacro.instantiatePluginsInCode(c)(referencablePlugins)
      val referenceStmt = c.Expr[List[PluginBase]](Liftable.liftList[Tree].apply(pluginsList))

      def lit[T: c.WeakTypeTag](s: T): c.Expr[T] = c.Expr[T](Literal(Constant(s)))
      def opt[T: c.WeakTypeTag](s: Option[T]): c.Expr[Option[T]] = {
        s match {
          case Some(value) => reify(Some[T](lit[T](value).splice))
          case None => reify(None)
        }
      }

      reify {
        val plugins = referenceStmt.splice
        new PerformPlanCheck[RoleAppMain, Roles, Activations, Config, CheckConfig, PrintPlan](
          roleAppMain.splice,
          lit[Roles](roles).splice,
          lit[Activations](activations).splice,
          lit[Config](config).splice,
          opt[CheckConfig](checkConfig).splice,
          opt[PrintPlan](printPlan).splice,
          plugins,
        )
      }
    }
  }

  // 2.12 requires `Witness`-like mechanism
  class Impl[
    RoleAppMain <: PlanHolder,
    Roles <: LiteralString,
    Activations <: LiteralString,
    Config <: LiteralString,
    CheckConfig <: LiteralBoolean,
    PrintPlan <: LiteralBoolean,
  ](@unused roleAppMain: RoleAppMain,
    @unused roles: Roles with LiteralString = LiteralString("*"),
    @unused activations: Activations with LiteralString = LiteralString("*"),
    @unused config: Config with LiteralString = LiteralString("*"),
    @unused checkConfig: CheckConfig with LiteralBoolean = LiteralBoolean.True,
    @unused printPlan: PrintPlan with LiteralBoolean = LiteralBoolean.False,
  )(implicit
    val planCheck: PerformPlanCheck[RoleAppMain, Roles#T, Activations#T, Config#T, CheckConfig#T, PrintPlan#T]
  ) {
    def rerunAtRuntime(): LoadedPlugins = planCheck.run()
  }

  private[PlanCheckMacro] final abstract class LiteralString { type T <: String }
  object LiteralString {
    @inline implicit final def apply(s: String): LiteralString { type T = s.type } = null
  }

  private[PlanCheckMacro] sealed abstract class LiteralBoolean { type T <: Boolean }
  object LiteralBoolean {
    @inline implicit final def apply(b: Boolean): LiteralBoolean = macro LiteralBooleanMacro.createBool

    @inline final def True: LiteralBoolean { type T = LiteralCompat.`true`.T } = null
    @inline final def False: LiteralBoolean { type T = LiteralCompat.`false`.T } = null

    object LiteralBooleanMacro {
      def createBool(c: whitebox.Context)(b: c.Expr[Boolean]): c.Tree = {
        import c.universe._
        val bool = b.tree.asInstanceOf[LiteralApi].value.value.asInstanceOf[Boolean]
        val methodName = TermName(bool.toString.capitalize)
        q"${reify(LiteralBoolean)}.$methodName"
      }
    }
  }

  // 2.13+
//  class Impl[T <: PlanHolder, Roles <: String with Singleton, Activations <: String with Singleton](
//    roleAppMain: T,
//    roles: Roles,
//    activations: Activations,
//  )(implicit
//    val planCheck: PlanCheck[T, Roles, Activations]
//  )

  private[this] def instantiateObject[T](u: Universe)(implicit tpe: u.WeakTypeTag[T]): T = {
    val className = s"${tpe.tpe.erasure.typeSymbol.fullName}$$"
    val clazz = Class.forName(className)
    TypeUtil.instantiateObject[T](clazz)
  }

}
