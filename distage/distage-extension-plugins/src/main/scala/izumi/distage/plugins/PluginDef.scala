package izumi.distage.plugins

import izumi.distage.model.definition.ModuleDef

import scala.language.experimental.macros
import scala.reflect.api.Universe
import scala.reflect.macros.whitebox

final abstract class ForcedRecompilationToken {
  type X
}

object ForcedRecompilationToken {
  implicit def materialize[T <: String]: ForcedRecompilationToken { type X = T } = macro UniqueRecompilationTokenMacro.whiteboxMaterializeImpl

  object UniqueRecompilationTokenMacro {
    final val compilerLaunchId = java.util.UUID.randomUUID().toString
    var cachedTypedTree: Universe#Tree = null

    def whiteboxMaterializeImpl(c: whitebox.Context): c.Tree = {
      import c.universe._
      UniqueRecompilationTokenMacro.synchronized {
        if (cachedTypedTree eq null) {
          val uuidStrConstantType = internal.constantType(Constant(compilerLaunchId))
          val tree1 = c.typecheck(q"null : _root_.izumi.distage.plugins.ForcedRecompilationToken { type X = $uuidStrConstantType }")
          cachedTypedTree = tree1
        }
        cachedTypedTree.asInstanceOf[c.Tree]
      }
    }
  }
}

abstract class PluginDef[T <: String](implicit val ev: ForcedRecompilationToken { type X = T }) extends PluginBase with ModuleDef {
  type RecompilationToken = T
}
