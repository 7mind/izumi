package izumi.distage.roles.model.definition

import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.definition.dsl.ModuleDefDSL.MakeDSL
import izumi.distage.reflection.macros.MakeMacro
import izumi.distage.roles.model.RoleDescriptor.GetRoleDescriptor
import izumi.distage.roles.model.definition.RoleModuleDef.RoleModuleDefMacros

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

trait RoleModuleDef extends ModuleDef {
  final protected[this] def makeRole[T](implicit getRoleDescriptor: GetRoleDescriptor[T]): MakeDSL[T] = macro RoleModuleDefMacros.makeRole[T]
}

object RoleModuleDef {

  object RoleModuleDefMacros {
    def makeRole[T: c.WeakTypeTag](c: blackbox.Context)(getRoleDescriptor: c.Expr[GetRoleDescriptor[T]]): c.Expr[MakeDSL[T]] = {
      c.universe.reify {
        MakeMacro.make[MakeDSL, T](c).splice.tagged(RoleTag(getRoleDescriptor.splice.roleDescriptor))
      }
    }
  }

}
