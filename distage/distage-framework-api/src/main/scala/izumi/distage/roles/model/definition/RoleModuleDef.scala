package izumi.distage.roles.model.definition

import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.definition.dsl.ModuleDefDSL
import izumi.distage.roles.model.RoleDescriptor
import izumi.distage.roles.model.RoleDescriptor.GetRoleDescriptor
import izumi.reflect.Tag

trait RoleModuleDef extends ModuleDef {

  final protected[this] def makeRole[T: Tag: GetRoleDescriptor]: ModuleDefDSL.MakeDSL[T] = {
    make[T].tagged(RoleTag(RoleDescriptor[T]))
  }

}
