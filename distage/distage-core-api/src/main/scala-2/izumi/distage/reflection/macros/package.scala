package izumi.distage.reflection

import izumi.fundamentals.reflection.ReflectionUtil

import scala.reflect.macros.blackbox

package object macros {

  private[macros] def requireConcreteTypeConstructor(c: blackbox.Context)(macroName: String, tpe: c.Type): Unit = {
    if (!ReflectionUtil.allPartsStrong(tpe.dealias.typeConstructor)) {
      c.abort(
        c.enclosingPosition,
        s"""$macroName: Can't generate constructor for $tpe:
           |Type constructor is an unresolved type parameter `${tpe.dealias.typeConstructor}`.
           |Did you forget to put a $macroName context bound on the ${tpe.dealias.typeConstructor}, such as [${tpe.dealias.typeConstructor}: $macroName]?
           |""".stripMargin,
      )
    }
  }

}
