package izumi.distage.roles.model.definition

import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.definition.dsl.ModuleDefDSL.MakeDSL
import izumi.distage.roles.model.RoleDescriptor.GetRoleDescriptor
import izumi.distage.constructors.AnyConstructorMacro
import izumi.distage.model.definition.dsl.ModuleDefDSL.MakeDSL
import scala.annotation.experimental
import scala.quoted.{Expr, Quotes, Type}

trait RoleModuleDef extends ModuleDef {
  inline final protected[this] def makeRole[T](implicit getRoleDescriptor: GetRoleDescriptor[T]): MakeDSL[T] =
    makeRoleImpl[T].tagged(RoleTag(getRoleDescriptor.roleDescriptor))

  inline final protected[this] def makeRoleImpl[T]: MakeDSL[T] =
    ${AnyConstructorMacro.makeMethod[T, MakeDSL[T]]}
}

object RoleModuleDef {

}
