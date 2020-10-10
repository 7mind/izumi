package izumi.distage.framework

import java.util.concurrent.ConcurrentHashMap

import com.typesafe.config.ConfigFactory
import distage.Injector
import izumi.distage.config.model.{AppConfig, ConfTag}
import izumi.distage.constructors.TraitConstructor
import izumi.distage.framework.model.ActivationInfo
import izumi.distage.framework.services.{ActivationChoicesExtractor, ConfigLoader}
import izumi.distage.model.definition.Axis.AxisValue
import izumi.distage.model.definition._
import izumi.distage.model.effect.DIEffectAsync
import izumi.distage.model.providers.Functoid
import izumi.distage.model.recursive.{BootConfig, Bootloader, LocatorRef}
import izumi.distage.model.reflection.{DIKey, SafeType}
import izumi.distage.plugins.PluginBase
import izumi.distage.roles.PlanHolder
import izumi.distage.roles.RoleAppMain.ArgV
import izumi.distage.roles.launcher.ActivationParser.activationKV
import izumi.distage.roles.launcher.{RoleAppActivationParser, RoleProvider}
import izumi.distage.roles.model.meta.{RoleBinding, RolesInfo}
import izumi.fundamentals.platform.cli.model.raw.RawAppArgs
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.unused
import izumi.fundamentals.platform.strings.IzString.{toRichIterable, toRichString}
import izumi.logstage.api.IzLogger
import izumi.reflect.TagK

import scala.language.experimental.macros
import scala.language.implicitConversions
import scala.reflect.macros.blackbox

object PlanCheck {
  private[this] final val defaultActivationsLimit = DebugProperties.`distage.plancheck.max-activations`.strValue().fold(9000)(_.asInt(9000))
  private[this] final val defaultCheckConfig = DebugProperties.`distage.plancheck.check-config`.boolValue(true)

  final class PerformPlanCheck[T <: PlanHolder, Roles <: String, Activations <: String, Config <: String](
    roleAppMain: T,
    roles: Roles,
    activations: Activations,
    config: Config,
  ) {
    def run(): Unit = checkRoleApp(roleAppMain, roles, activations, config)
  }
  object PerformPlanCheck {
    implicit def performCompileTimeCheck[T <: PlanHolder, Roles <: String, Activations <: String, Config <: String]: PerformPlanCheck[T, Roles, Activations, Config] =
      macro Materialize.impl[T, Roles, Activations, Config]
  }

  object Materialize {
    def impl[T <: PlanHolder: c.WeakTypeTag, Roles <: String: c.WeakTypeTag, Activations <: String: c.WeakTypeTag, Config <: String: c.WeakTypeTag](
      c: blackbox.Context
    ): c.Expr[PerformPlanCheck[T, Roles, Activations, Config]] = {
      import c.universe._

      def getConstantType[S: c.WeakTypeTag]: String = {
        weakTypeOf[S].dealias match {
          case ConstantType(Constant(s: String)) => s
          case tpe =>
            c.abort(
              c.enclosingPosition,
              s"""When materializing PlanCheck[${weakTypeOf[T]}, ${weakTypeOf[Roles]}, ${weakTypeOf[Activations]}],
                 |Bad String constant type: $tpe - Not a constant! Only constant string literal types are supported!
               """.stripMargin,
            )
        }
      }

      val t = c.Expr[T](q"${weakTypeOf[T].asInstanceOf[SingleTypeApi].sym.asTerm}")
      val roles = c.Expr[Roles](Liftable.liftString(getConstantType[Roles]))
      val activations = c.Expr[Activations](Liftable.liftString(getConstantType[Activations]))
      val config = c.Expr[Config](Liftable.liftString(getConstantType[Config]))

      // err, do in place?

      // FIXME: splice plugin references

      reify {
        new PerformPlanCheck[T, Roles, Activations, Config](t.splice, roles.splice, activations.splice, config.splice)
      }
    }
  }

  // 2.12 requires `Witness`
  class Impl[RoleAppMain <: PlanHolder, Roles <: LiteralString, Activations <: LiteralString, Config <: LiteralString](
    roleAppMain: RoleAppMain,
    roles: Roles with LiteralString = LiteralString("*"),
    activations: Activations with LiteralString = LiteralString("*"),
    config: Config with LiteralString = LiteralString("*"),
  )(implicit
    val planCheck: PerformPlanCheck[RoleAppMain, Roles#T, Activations#T, Config#T]
  )
  private[PlanCheck] final abstract class LiteralString { type T <: String }
  object LiteralString {
    @inline implicit final def apply(s: String): LiteralString { type T = s.type } = null
  }
  private[PlanCheck] final abstract class LiteralBoolean { type T <: Boolean }
  object LiteralBoolean {
    @inline implicit final def apply(b: Boolean): LiteralBoolean { type T = b.type } = null
  }

  // 2.13+
//  class Impl[T <: PlanHolder, Roles <: String with Singleton, Activations <: String with Singleton](
//    roleAppMain: T,
//    roles: Roles,
//    activations: Activations,
//  )(implicit
//    val planCheck: PlanCheck[T, Roles, Activations]
//  )

  def checkRoleApp(
    roleAppMain: PlanHolder,
    roles: String = "*",
    activations: String = "*",
    config: String = "*",
    limit: Int = defaultActivationsLimit,
  ): Unit = {
    val chosenRoles = if (roles == "*") None else Some(parseRoles(roles))
    val chosenActivations = if (activations == "*") None else Some(parseActivations(activations))
    val chosenConfig = if (config == "*") None else Some(config)
    checkRoleAppParsed(roleAppMain, chosenRoles, chosenActivations, chosenConfig, limit)
  }

  def checkRoleAppParsed(
    roleAppMain: PlanHolder,
    chosenRoles: Option[Set[String]],
    chosenActivations: Option[Array[Iterable[(String, String)]]],
    chosenConfig: Option[String],
    limit: Int,
  ): Unit = {

    val roleAppBootstrapModule = roleAppMain.finalAppModule(ArgV(Array.empty))
    Injector[Identity]().produceRun(roleAppBootstrapModule overriddenBy new ModuleDef {
      make[IzLogger].named("early").fromValue(IzLogger.NullLogger)
      make[IzLogger].fromValue(IzLogger.NullLogger)
      make[AppConfig].fromValue(AppConfig.empty)
      make[RawAppArgs].fromValue(RawAppArgs.empty)
      make[Activation].named("roleapp").todo // TODO
      make[RoleProvider].from {
        chosenRoles match {
          case None =>
            @impl trait AllRolesProvider extends RoleProvider.Impl {
              override protected def isRoleEnabled(requiredRoles: Set[String])(b: RoleBinding): Boolean = true
            }
            TraitConstructor[AllRolesProvider]
          case Some(chosenRoles) =>
            @impl trait ConfiguredRoleProvider extends RoleProvider.Impl {
              override protected def getInfo(bindings: Set[Binding], @unused requiredRoles: Set[String], roleType: SafeType): RolesInfo = {
                super.getInfo(bindings, chosenRoles, roleType)
              }
            }
            TraitConstructor[ConfiguredRoleProvider]
        }
      }
      make[ConfigLoader.Args].from {
        chosenRoles match {
          case Some(roleNames) =>
            Functoid(() => ConfigLoader.Args.forEnabledRoles(roleNames))
          case None =>
            // use all roles
            Functoid(ConfigLoader.Args forEnabledRoles (_: RolesInfo).availableRoleNames)
        }
      }
      chosenConfig.foreach {
        resourceName =>
          make[ConfigLoader].fromValue[ConfigLoader](() => AppConfig(ConfigFactory.parseResources(resourceName)))
      }
    })(Functoid {
      (
        bootloader: Bootloader @Id("roleapp"),
        bsModule: BootstrapModule @Id("roleapp"),
        activationChoicesExtractor: ActivationChoicesExtractor,
        roleAppActivationParser: RoleAppActivationParser,
        configLoader: ConfigLoader,
//        roots: Set[DIKey] @Id("distage.roles.roots"),
        activationInfo: ActivationInfo,
        locatorRef: LocatorRef,
        appPlugins: Seq[PluginBase] @Id("main"),
        bsPlugins: Seq[PluginBase] @Id("main"),
      ) =>
        val allChoices = chosenActivations match {
          case None =>
            allActivations(activationChoicesExtractor, bootloader.input.bindings, limit)
          case Some(choiceSets) =>
            choiceSets.iterator.map(roleAppActivationParser.parseActivation(_, activationInfo)).toSet
        }
        println(allChoices.niceList())
        println(locatorRef.get.plan)

        val chm = ConcurrentHashMap.newKeySet[AppConfig => Any]()

        val allKeysFromRoleAppMainModule = {
          val keysUsedInBootstrap = locatorRef.get.allInstances.iterator.map(_.key).toSet
          val keysThatCouldveBeenInBootstrap = roleAppBootstrapModule.keys
          keysUsedInBootstrap ++ keysThatCouldveBeenInBootstrap
        }

        //allChoices.foreach {
        DIEffectAsync.diEffectParIdentity.parTraverse_(allChoices) {
          checkPlanJob(bootloader, bsModule, allKeysFromRoleAppMainModule, chm)
        }

        val realAppConfig = configLoader.loadConfig()
        chm.forEach(_.apply(realAppConfig))
    })
  }

  private[this] def checkPlanJob[F[_]: TagK](
    bootloader: Bootloader,
    bsModule: BootstrapModule,
    allKeysFromRoleAppMainModule: Set[DIKey],
    chm: ConcurrentHashMap.KeySetView[AppConfig => Any, java.lang.Boolean],
  )(activation: Activation
  ): Unit = {
    val app = bootloader.boot(
      BootConfig(
        bootstrap = _ => bsModule,
        activation = _ => activation,
      )
    )
    println(s"\n\n\n$activation\n\n\n")
    println(app.plan)
    app.plan.assertValidOrThrow[F](k => allKeysFromRoleAppMainModule(k) || test_ignore(k))

    // instead of partially evaluating the plan and running into problems because, due to mutators, even `makeConfig`
    // bindings can have arbitrary relationships with the rest of the graph which would force us to run the check
    // for all possible activations, we just collect all the parsers added via `makeConfig` and execute them on
    // an `AppConfig` once to check that the default config is well-formed.
    app.plan.steps.foreach {
      _.origin
        .value.fold(
          (),
          _.tags.foreach {
            case c: ConfTag => chm.add(c.parser)
            case _ => ()
          },
        )
    }
  }

  private[this] def test_ignore(target: DIKey): Boolean = {
    target.tpe.tag.shortName.contains("XXX_LocatorLeak")
  }

  private[this] def parseRoles(s: String): Set[String] = {
    s.split(" ").iterator.filter(_.nonEmpty).map(_.stripPrefix(":")).toSet
  }

  private[this] def parseActivations(s: String): Array[Iterable[(String, String)]] = {
    s.split(" *\\| *").map(_.split(" ").iterator.filter(_.nonEmpty).map(activationKV).toSeq)
  }

  private[this] def allActivations(activationChoicesExtractor: ActivationChoicesExtractor, bindings: ModuleBase, limit: Int): Set[Activation] = {
    var counter = 0
    var printed = false
    def go(accumulator: Activation, axisValues: Map[Axis, Set[AxisValue]]): Set[Activation] = {
      if (counter >= limit) {
        if (!printed) {
          printed = true
          System.err.println(s"WARN: too many possible activations, over $limit, will not consider further possibilities (check back soon for a non-bruteforce checker)")
        }
        Set.empty
      } else {
        axisValues.headOption match {
          case Some((axis, allChoices)) =>
            allChoices.flatMap {
              choice =>
                go(accumulator ++ Activation(axis -> choice), axisValues.tail)
            }
          case None =>
            counter += 1
            Set(accumulator)
        }
      }
    }
    val allPossibleChoices = activationChoicesExtractor.findAvailableChoices(bindings).availableChoices
    go(Activation.empty, allPossibleChoices)
  }

}
