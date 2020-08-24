package izumi.distage.plugins

import izumi.distage.model.definition.ModuleDef

import scala.language.experimental.macros
import scala.reflect.api.Universe
import scala.reflect.macros.whitebox

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

abstract class PluginDef[T <: String](implicit val ev: ForcedRecompilationToken[T]) extends PluginBase with ModuleDef {
  type RecompilationToken = T
}
