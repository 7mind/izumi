package izumi.distage.plugins

import scala.language.experimental.macros
import scala.reflect.api.Universe
import scala.reflect.macros.whitebox

/**
  * This macro enables `distage`'s compile-time checks to work well with Scala's incremental compilation,
  * it forces recompilation of the macro that performs compile-time plan checking every time a PluginDef,
  * or a constructor bound in PluginDef changes. It does that by generating a new unique type for each
  * compiler session and assigning it to a class inheriting PluginDef. The "change of super type" of a plugin
  * forces recompilation of all code that references it, and specifically the [[izumi.distage.staticinjector.plugins.StaticPluginChecker]] macro.
  * This allows compile-time checking macro to provide rapid feedback during development.
  *
  * @see [[https://izumi.7mind.io/distage/distage-framework.html#compile-time-checks Compile-time checks]]
  */
final abstract class ForcedRecompilationToken[T] {
  type Token = T
}

object ForcedRecompilationToken {
  implicit def materialize[T <: String]: ForcedRecompilationToken[T] = macro UniqueRecompilationTokenMacro.whiteboxMaterializeImpl

  object UniqueRecompilationTokenMacro {
    final val compilerLaunchId = java.util.UUID.randomUUID().toString
    var cachedTypedTree: Universe#Tree = null

    def whiteboxMaterializeImpl(c: whitebox.Context): c.Tree = {
      import c.universe._
      UniqueRecompilationTokenMacro.synchronized {
        if (cachedTypedTree eq null) {
          val uuidStrConstantType = internal.constantType(Constant(compilerLaunchId))
          val tree = c.typecheck(q"null : _root_.izumi.distage.plugins.ForcedRecompilationToken[$uuidStrConstantType]")
          cachedTypedTree = tree
        }
        cachedTypedTree.asInstanceOf[c.Tree]
      }
    }
  }
}
