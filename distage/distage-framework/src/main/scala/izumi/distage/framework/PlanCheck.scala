package izumi.distage.framework

import distage.Injector
import izumi.distage.config.model.AppConfig
import izumi.distage.constructors.TraitConstructor
import izumi.distage.framework.PlanCheck.{checkRoleApp, checkRoleAppParsed}
import izumi.distage.framework.model.ActivationInfo
import izumi.distage.framework.services.ActivationChoicesExtractor
import izumi.distage.model.definition.Axis.AxisValue
import izumi.distage.model.definition._
import izumi.distage.model.plan.ExecutableOp.ImportDependency
import izumi.distage.model.providers.Functoid
import izumi.distage.model.recursive.{BootConfig, Bootloader, LocatorRef}
import izumi.distage.model.reflection.{DIKey, SafeType}
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

import scala.language.experimental.macros
import scala.language.implicitConversions
import scala.reflect.macros.blackbox

final class PlanCheck[T <: PlanHolder, Roles <: String, Activations <: String](
  roleAppMain: T,
  roles: Roles,
  activations: Activations,
) {
  def run(): Unit = checkRoleApp(roleAppMain, roles, activations)
}

object PlanCheck {

  implicit def performCompileTimeCheck[T <: PlanHolder, Roles <: String, Activations <: String]: PlanCheck[T, Roles, Activations] =
    macro Materialize212.impl[T, Roles, Activations]

  object Materialize212 {
    def impl[T <: PlanHolder: c.WeakTypeTag, Roles <: String: c.WeakTypeTag, Activations <: String: c.WeakTypeTag](
      c: blackbox.Context
    ): c.Expr[PlanCheck[T, Roles, Activations]] = {
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

      // err, do in place?
      reify {
        new PlanCheck[T, Roles, Activations](t.splice, roles.splice, activations.splice)
      }
    }
  }

  // 2.12 requires `Witness`
  class Impl[T <: PlanHolder, Roles <: Witness, Activations <: Witness](
    roleAppMain: T,
    roles: Roles with Witness = Witness.fromString("*"),
    activations: Activations with Witness = Witness.fromString("*"),
  )(implicit
    val planCheck: PlanCheck[T, Roles#T, Activations#T]
  )
  private[PlanCheck] final abstract class Witness { type T <: String }
  object Witness {
    implicit def fromString(s: String): Witness { type T = s.type } = null
  }

  // 2.13+
//  class Impl[T <: PlanHolder, Roles <: String with Singleton, Activations <: String with Singleton](
//    roleAppMain: T,
//    roles: Roles,
//    activations: Activations,
//  )(implicit
//    val planCheck: PlanCheck[T, Roles#T, Activations#T]
//  )

  def checkRoleApp(roleAppMain: PlanHolder, roles: String = "*", activations: String = "*", limit: Int = defaultActivationsLimit): Unit = {
    val chosenRoles = if (roles == "*") None else Some(parseRoles(roles))
    val chosenActivations = if (activations == "*") None else Some(parseActivations(activations))
    checkRoleAppParsed(roleAppMain, chosenRoles, chosenActivations, limit)
  }

  def checkRoleAppParsed(
    roleAppMain: PlanHolder,
    chosenRoles: Option[Set[String]],
    chosenActivations: Option[Array[Iterable[(String, String)]]],
    limit: Int,
  ): Unit = {
    import roleAppMain.{AppEffectType, tagK}

    val roleAppBootstrapModule = roleAppMain.finalAppModule(ArgV(Array.empty))
    Injector[Identity]().produceRun(roleAppBootstrapModule overriddenBy new ModuleDef {
      make[IzLogger].fromValue(IzLogger.NullLogger)
      make[AppConfig].fromValue(AppConfig.empty)
      make[RawAppArgs].fromValue(RawAppArgs.empty)
      make[Activation].named("roleapp").todo
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
    })(Functoid {
      (
        bootloader: Bootloader @Id("roleapp"),
        bsModule: BootstrapModule @Id("roleapp"),
        activationChoicesExtractor: ActivationChoicesExtractor,
        roleAppActivationParser: RoleAppActivationParser,
//        roots: Set[DIKey] @Id("distage.roles.roots"),
        activationInfo: ActivationInfo,
        locatorRef: LocatorRef,
      ) =>
        val allChoices = chosenActivations match {
          case None => allActivations(activationChoicesExtractor, bootloader.input.bindings, limit)
          case Some(choiceSets) => choiceSets.iterator.map(roleAppActivationParser.parseActivation(_, activationInfo)).toSet
        }
        println(allChoices.niceList())
        println(locatorRef.get.plan)
//        DIEffectAsync.diEffectParIdentity.parTraverse_(allChoices) {
        allChoices.foreach {
          activation =>
            val app = bootloader.boot(
              BootConfig(
                bootstrap = _ => bsModule,
                activation = _ => activation,
              )
            )
            val keysUsedInBootstrap = locatorRef.get.allInstances.iterator.map(_.key).toSet
            val futureKeys = roleAppBootstrapModule.keys
            val allKeys = keysUsedInBootstrap ++ futureKeys

            println(s"\n\n\n$activation\n\n\n")
            println(allKeys.map(_.toString).niceList())
            println(app.plan)
            app
              .plan
              .resolveImports {
                case ImportDependency(target, _, _) if allKeys(target) || _test_ignore(target) => null
              }
              .assertValidOrThrow[AppEffectType]()
        }
    })
  }

  private[this] def _test_ignore(target: DIKey): Boolean = {
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
    def go(accum: Activation, axisValues: Map[Axis, Set[AxisValue]]): Set[Activation] = {
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
                go(accum ++ Activation(axis -> choice), axisValues.tail)
            }
          case None =>
            counter += 1
            Set(accum)
        }
      }
    }
    val allPossibleChoices = activationChoicesExtractor.findAvailableChoices(bindings).availableChoices
    go(Activation.empty, allPossibleChoices)
  }

  private[this] final val defaultActivationsLimit = DebugProperties.`distage.plancheck.max-activations`.strValue().fold(9000)(_.asInt(9000))

}
