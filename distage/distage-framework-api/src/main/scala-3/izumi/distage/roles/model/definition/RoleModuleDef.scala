package izumi.distage.roles.model.definition

import izumi.distage.constructors.MakeMacro
import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.definition.dsl.ModuleDefDSL.MakeDSL
import izumi.distage.roles.model.RoleDescriptor.GetRoleDescriptor
import izumi.distage.roles.model.definition.RoleModuleDef.RoleModuleDefMacros

import scala.quoted.{Expr, Quotes, Type}

trait RoleModuleDef extends ModuleDef {
  inline final protected[this] def makeRole[T](implicit getRoleDescriptor: GetRoleDescriptor[T]): MakeDSL[T] =
    ${ RoleModuleDefMacros.makeRole[T]('getRoleDescriptor) }
}

object RoleModuleDef {

  object RoleModuleDefMacros {
    def makeRole[T: Type](getRoleDescriptor: Expr[GetRoleDescriptor[T]])(using qctx: Quotes): Expr[MakeDSL[T]] = {
      '{
        ${ MakeMacro.makeMethod[T, MakeDSL[T]] }.tagged(RoleTag(${ getRoleDescriptor }.roleDescriptor))
      }
    }
  }

}
