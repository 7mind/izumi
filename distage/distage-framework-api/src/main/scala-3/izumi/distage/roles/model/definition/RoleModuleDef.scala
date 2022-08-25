package izumi.distage.roles.model.definition

import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.definition.dsl.ModuleDefDSL.MakeDSL
import izumi.distage.roles.model.RoleDescriptor.GetRoleDescriptor


trait RoleModuleDef extends ModuleDef {
  final protected[this] def makeRole[T](implicit getRoleDescriptor: GetRoleDescriptor[T]): MakeDSL[T] = ???
}

object RoleModuleDef {


}
