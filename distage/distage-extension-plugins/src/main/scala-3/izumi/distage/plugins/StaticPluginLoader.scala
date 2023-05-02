package izumi.distage.plugins

import izumi.distage.plugins.load.{LoadedPlugins, PluginLoaderDefaultImpl}

import scala.compiletime.error
import scala.quoted.{Expr, Quotes}

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

    val quoted = instantiatePluginsInCode(loadedPlugins.result)

    Expr.ofList(quoted)
  }

  def instantiatePluginsInCode(loadedPlugins: Seq[PluginBase])(using qctx: Quotes): List[Expr[PluginBase]] = {
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

        if (clsSym.flags.is(Flags.Module) || clsSym.isTerm) {
          val objRef = clsSym.companionModule.termRef
          Ref.term(objRef).asExprOf[PluginBase]
        } else if (clsSym.isType && clsSym.isClassDef) {
          Typed(Apply(Select(New(TypeIdent(clsSym)), clsSym.primaryConstructor), Nil), TypeTree.ref(clsSym)).asExprOf[PluginBase]
        } else {
          report.errorAndAbort(
            s"Couldn't reflect runtime class of `${plugin.getClass}`, got non-type and non-object symbol=$clsSym typeRef=${clsSym.typeRef} companionModule=${clsSym.companionModule} companionClass=${clsSym.companionClass}"
          )
        }
    }.toList
  }

}
