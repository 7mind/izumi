package izumi.distage.roles.test.fixtures

import distage.{Axis, ModuleDef}
import izumi.distage.config.ConfigModuleDef
import izumi.distage.model.definition.Lifecycle
import izumi.distage.roles.model.{RoleDescriptor, RoleService}
import izumi.distage.roles.model.definition.RoleModuleDef
import izumi.distage.roles.test.fixtures.TestRole05.{TestRole05Dependency, TestRole05DependencyImpl1}
import izumi.functional.quasi.QuasiIO
import izumi.fundamentals.platform.cli.model.raw.RawEntrypointParams
import izumi.reflect.TagK

class TestRole05[F[_] : QuasiIO](
                                  dependency: TestRole05Dependency
                                ) extends RoleService[F] {
  override def start(roleParameters: RawEntrypointParams, freeArgs: Vector[String]): Lifecycle[F, Unit] = Lifecycle.make(QuasiIO[F].maybeSuspend {
    assert(dependency.isInstanceOf[TestRole05DependencyImpl1])
  }) {
    _ =>
      QuasiIO[F].unit
  }
}

object TestRole05 extends RoleDescriptor {
  override final val id = "testrole05"

  trait TestRole05Dependency

  class TestRole05DependencyImpl1 extends TestRole05Dependency

  class TestRole05DependencyImpl2 extends TestRole05Dependency

  object Role05LocalAxis extends Axis {
    case object Rolelocal2 extends AxisChoiceDef

    case object Rolelocal1 extends AxisChoiceDef
  }

  class Role05Module[F[_] : TagK] extends ModuleDef with ConfigModuleDef with RoleModuleDef {
    makeRole[TestRole05[F]]
    make[TestRole05Dependency].from[TestRole05DependencyImpl1].tagged(Role05LocalAxis.Rolelocal1)
    make[TestRole05Dependency].from[TestRole05DependencyImpl2].tagged(Role05LocalAxis.Rolelocal2)
  }

}