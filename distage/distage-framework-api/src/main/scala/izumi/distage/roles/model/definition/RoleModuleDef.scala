package izumi.distage.roles.model.definition

import izumi.distage.constructors.macros.AnyConstructorMacro
import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.definition.dsl.ModuleDefDSL.MakeDSL
import izumi.distage.roles.model.RoleDescriptor.GetRoleDescriptor

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

trait RoleModuleDef extends ModuleDef {

  final protected[this] def makeRole[T](implicit getRoleDescriptor: GetRoleDescriptor[T]): MakeDSL[T] =
    macro RoleModuleDef.RoleModuleDefMacros.makeRole[T]

}

object RoleModuleDef {

  object RoleModuleDefMacros {
    def makeRole[T: c.WeakTypeTag](c: blackbox.Context)(getRoleDescriptor: c.Expr[GetRoleDescriptor[T]]): c.Expr[MakeDSL[T]] = {
      c.universe.reify {
        AnyConstructorMacro.make[MakeDSL, T](c).splice.tagged(RoleTag(getRoleDescriptor.splice.roleDescriptor))
      }
    }
  }

}
