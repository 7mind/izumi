package izumi.distage.roles.test.fixtures

import distage.{Axis, ModuleDef}
import izumi.distage.config.ConfigModuleDef
import izumi.distage.model.definition.Lifecycle
import izumi.distage.roles.model.definition.RoleModuleDef
import izumi.distage.roles.model.{RoleDescriptor, RoleService}
import izumi.distage.roles.test.fixtures.TestRole05.{TestRole05Dependency, TestRole05DependencyImpl1}
import izumi.functional.bio.IO2
import izumi.fundamentals.platform.cli.model.EntrypointArgs
import izumi.fundamentals.platform.uuid.IzUUID
import izumi.reflect.TagKK

import scala.annotation.unused

class TestRole05[F[+_, +_]: IO2](
  dependency: TestRole05Dependency,
  @unused uuid: IzUUID,
) extends RoleService[F] {
  override def start(roleParameters: EntrypointArgs): Lifecycle[F, Throwable, Unit] = Lifecycle.make(IO2[F].syncThrowable {
    assert(dependency.isInstanceOf[TestRole05DependencyImpl1])
  }) {
    _ =>
      IO2[F].unit
  }
}

object TestRole05 extends RoleDescriptor {
  override final val id = "testrole05"

  trait TestRole05Dependency

  class TestRole05DependencyImpl1(val roleSpecificConfig: Rolelocal1SpecificConfig) extends TestRole05Dependency

  class TestRole05DependencyImpl2(val roleSpecificConfig: Rolelocal2SpecificConfig) extends TestRole05Dependency

  object Role05LocalAxis extends Axis {
    case object Rolelocal2 extends AxisChoiceDef
    case object Rolelocal1 extends AxisChoiceDef
  }

  final case class Rolelocal2SpecificConfig(bool: Boolean)
  case class Dummy(a: Int, b: String)
  final case class Rolelocal1SpecificConfig(str: String, dummy: Option[Dummy])

  class Role05Module[F[+_, +_]: TagKK] extends ModuleDef with ConfigModuleDef with RoleModuleDef {
    makeRole[TestRole05[F]]
    make[TestRole05Dependency].from[TestRole05DependencyImpl1].tagged(Role05LocalAxis.Rolelocal1)
    make[TestRole05Dependency].from[TestRole05DependencyImpl2].tagged(Role05LocalAxis.Rolelocal2)
    makeConfig[Rolelocal1SpecificConfig]("rolelocal1")
    makeConfig[Rolelocal2SpecificConfig]("rolelocal2")
  }

}
