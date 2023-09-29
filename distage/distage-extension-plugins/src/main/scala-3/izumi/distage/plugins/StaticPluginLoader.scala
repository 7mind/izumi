package izumi.distage.plugins

import izumi.distage.model.definition.ModuleBase
import izumi.distage.plugins.load.{LoadedPlugins, PluginLoaderDefaultImpl}

import scala.compiletime.error
import scala.quoted.{Expr, Quotes, Type}

/** Scan the specified package *at compile-time* for classes and objects that inherit [[PluginBase]]
  *
  * WARN: may interact badly with incremental compilation
  * WARN: will _not_ find plugins defined in the current module, only those defined in dependency modules
  *       (similarly to how you cannot call Scala macros defined in the current module)
  *
  * @see [[PluginConfig.compileTime]]
  */
object StaticPluginLoader {

  inline def scanCompileTime(inline pluginsPackage: String): List[PluginBase] = ${ scanCompileTimeImpl('{ pluginsPackage }) }

  def scanCompileTimeImpl(pluginsPackageExpr: Expr[String])(using qctx: Quotes): Expr[List[PluginBase]] = {
    val pluginPath = pluginsPackageExpr.valueOrAbort

    val loadedPlugins = if (pluginPath == "") {
      LoadedPlugins.empty
    } else {
      new PluginLoaderDefaultImpl().load(PluginConfig.packages(Seq(pluginPath)))
    }

    val quoted = instantiatePluginsInCode[PluginBase](loadedPlugins.loaded)

    Expr.ofList(quoted)
  }

  def instantiatePluginsInCode[T <: ModuleBase: Type](loadedPlugins: Seq[T])(using qctx: Quotes): List[Expr[T]] = {
    import qctx.reflect.*

    loadedPlugins.map {
      plugin =>
        val canonicalName = plugin.getClass.getCanonicalName
        val clsSym = if (canonicalName.endsWith("$")) {
          Symbol.requiredModule(canonicalName.stripSuffix("$"))
        } else {
          Symbol.requiredClass(canonicalName)
        }

        val tpe = clsSym.typeRef

        val term = if (clsSym.flags.is(Flags.Module) || clsSym.isTerm) {
          val objRef = clsSym.companionModule.termRef
          Ref.term(objRef)
        } else if (clsSym.isType && clsSym.isClassDef) {
          Typed(Apply(Select(New(TypeIdent(clsSym)), clsSym.primaryConstructor), Nil), TypeTree.ref(clsSym))
        } else {
          report.errorAndAbort(
            s"Couldn't reflect runtime class of `${plugin.getClass}`, got non-type and non-object symbol=$clsSym typeRef=${clsSym.typeRef} companionModule=${clsSym.companionModule} companionClass=${clsSym.companionClass}"
          )
        }
        Typed(term, TypeTree.of[T]).asExprOf[T]
    }.toList
  }

}
