package izumi.distage.framework

import distage.{Injector, TraitConstructor}
import izumi.distage.config.model.AppConfig
import izumi.distage.framework.services.ActivationChoicesExtractor
import izumi.distage.model.definition.Axis.AxisValue
import izumi.distage.model.definition.StandardAxis.Mode
import izumi.distage.model.definition._
import izumi.distage.model.effect.DIEffectAsync
import izumi.distage.model.plan.ExecutableOp.ImportDependency
import izumi.distage.model.providers.Functoid
import izumi.distage.model.recursive.{BootConfig, Bootloader, LocatorRef}
import izumi.distage.model.reflection.DIKey
import izumi.distage.roles.RoleAppMain
import izumi.distage.roles.RoleAppMain.ArgV
import izumi.distage.roles.launcher.ActivationParser.activationKV
import izumi.distage.roles.launcher.RoleProvider
import izumi.distage.roles.model.meta.RoleBinding
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.strings.IzString.toRichIterable

import scala.util.Try

object PlanCheck {

  def checkRoleApp[F[_]](roleAppMain: RoleAppMain[F], roles: String = "*", activations: String = "*"): Unit = {
    val chosenRoles = if (roles == "*") None else Some(parseRoles(roles))
    val chosenActivations = if (activations == "*") None else Some(parseActivations(activations))
    checkRoleApp(roleAppMain, chosenRoles, chosenActivations)
  }

  def checkRoleApp[F[_]](roleAppMain: RoleAppMain[F], chosenRoles: Option[Set[String]], chosenActivations: Option[Array[Iterable[(String, String)]]]): Unit = {
    import roleAppMain.tagK

    val roleAppBootstrapModule = roleAppMain.finalAppModule(ArgV(Array.empty))
    Injector[Identity]().produceRun(roleAppBootstrapModule overriddenBy new ModuleDef {
      make[RoleProvider].from {
        @impl trait ConfiguredRoleProvider extends RoleProvider.Impl {
          override protected def isRoleEnabled(requiredRoles: Set[String])(b: RoleBinding): Boolean = {
            chosenRoles.fold(true)(_ contains b.descriptor.id)
          }
        }
        TraitConstructor[ConfiguredRoleProvider]
      }
      make[AppConfig].fromValue(AppConfig.empty)
      make[Activation].named("roleapp").fromValue(Activation.empty)
    })(Functoid {
      (bootloader: Bootloader @Id("roleapp"), bsModule: BootstrapModule @Id("roleapp"), locatorRef: LocatorRef) =>
        val allChoices = chosenActivations match {
          case None => allActivations(new ActivationChoicesExtractor.Impl, bootloader.input.bindings)
          case Some(_) => ???
        }
        DIEffectAsync.diEffectParIdentity.parTraverse_(allChoices) {
          activation =>
            println(Try {
              val app = bootloader.boot(
                BootConfig(
                  bootstrap = _ => bsModule,
                  activation = _ => activation,
                )
              )
              val keysUsedInBootstrap = locatorRef.get.allInstances.iterator.map(_.key).toSet
              val futureKeys = roleAppBootstrapModule.keys
              val allKeys = keysUsedInBootstrap ++ futureKeys

              println(allKeys.map(_.toString).niceList())
              println(app.plan.render())
              app
                .plan
                .resolveImports {
                  case ImportDependency(target, _, _) if allKeys(target) || _test_ignore(target) => null
                }
                .assertValidOrThrow[F]()
            })
        }
    })
  }

  private[this] def _test_ignore(target: DIKey): Boolean = {
    target.tpe.tag.shortName.contains("XXX_LocatorLeak")
  }

  private[this] def parseRoles(s: String): Set[String] = {
    s.split(" ").toSet
  }

  private[this] def parseActivations(s: String): Array[Iterable[(String, String)]] = {
    s.split(" *\\| *").map(_.split(" ").map(activationKV).toIterable)
  }

  private[this] def allActivations(activationChoicesExtractor: ActivationChoicesExtractor, bindings: ModuleBase): Set[Activation] = {
    def go(accum: Activation, axisValues: Map[Axis, Set[AxisValue]]): Set[Activation] = {
      axisValues.headOption match {
        case Some((axis, allChoices)) =>
          allChoices.flatMap {
            choice =>
              go(accum ++ Activation(axis -> choice), axisValues.tail)
          }
        case None =>
          Set(accum)
      }
    }
    val allPossibleChoices = activationChoicesExtractor.findAvailableChoices(bindings).availableChoices
    go(Activation.empty, allPossibleChoices)
  }

}
