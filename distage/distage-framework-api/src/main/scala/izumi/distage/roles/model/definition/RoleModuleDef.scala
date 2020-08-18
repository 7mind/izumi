package izumi.distage.roles.model.definition

import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.definition.dsl.ModuleDefDSL
import izumi.distage.roles.model.RoleDescriptor.GetRoleDescriptor

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

trait RoleModuleDef extends ModuleDef {

  final protected[this] def makeRole[T](implicit getRoleDescriptor: GetRoleDescriptor[T]): ModuleDefDSL.MakeDSL[T] =
    macro RoleModuleDef.RoleModuleDefMacros.makeRole[T]

}

object RoleModuleDef {

  object RoleModuleDefMacros {
    def makeRole[T: c.WeakTypeTag](c: blackbox.Context)(getRoleDescriptor: c.Expr[GetRoleDescriptor[T]]): c.Expr[ModuleDefDSL.MakeDSL[T]] = {
      import c.universe._
      val makeExpr = c.Expr[ModuleDefDSL.MakeDSL[T]] {
        q"${c.prefix}.make[${weakTypeOf[T]}]"
      }
      reify {
        makeExpr.splice.tagged(RoleTag(getRoleDescriptor.splice.roleDescriptor))
      }
    }
  }

}
