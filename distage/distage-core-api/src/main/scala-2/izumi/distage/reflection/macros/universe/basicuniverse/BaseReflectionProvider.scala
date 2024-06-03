package izumi.distage.reflection.macros.universe.basicuniverse

import izumi.distage.model.definition.Id
import izumi.distage.model.exceptions.macros.reflection.BadIdAnnotationException
import izumi.distage.reflection.macros.universe.basicuniverse

import scala.reflect.macros.blackbox

class BaseReflectionProvider(val c: blackbox.Context) {
  private val idAnnotationFqn = c.typeOf[Id].typeSymbol.fullName

  def typeToParameter(t: scala.reflect.api.Universe#Type): CompactParameter = {
    parameterToAssociation2(MacroSymbolInfoCompactImpl.syntheticFromType(c.universe)(c.freshName)(t.asInstanceOf[c.universe.Type]))
  }

  def symbolToParameter(s: scala.reflect.api.Universe#Symbol): CompactParameter = {
    parameterToAssociation2(MacroSymbolInfoCompactImpl.fromSymbol(c.universe)(s.asInstanceOf[c.universe.Symbol]))
  }

  private def parameterToAssociation2(parameterSymbol: MacroSymbolInfoCompact): CompactParameter = {
    val key = keyFromSymbol(parameterSymbol)
    basicuniverse.CompactParameter(parameterSymbol, tpeFromSymbol(parameterSymbol), key)
  }

  def tpeFromSymbol(parameterSymbol: MacroSymbolInfoCompact): MacroSafeType = {
    val paramType = if (parameterSymbol.isByName) { // this will never be true for a method symbol
      parameterSymbol.finalResultType.typeArgs.head.finalResultType
    } else {
      parameterSymbol.finalResultType
    }
    MacroSafeType.create(c.universe)(paramType.asInstanceOf[c.Type])
  }

  def keyFromSymbol(parameterSymbol: MacroSymbolInfoCompact): MacroDIKey.BasicKey = {
    val tpe = tpeFromSymbol(parameterSymbol)
    val typeKey = MacroDIKey.TypeKey(tpe)
    withIdKeyFromAnnotation(parameterSymbol, typeKey)
  }

  private[this] def withIdKeyFromAnnotation(parameterSymbol: MacroSymbolInfoCompact, typeKey: MacroDIKey.TypeKey): MacroDIKey.BasicKey = {
    val maybeDistageName = parameterSymbol.findUniqueFriendlyAnno(a => a.fqn == idAnnotationFqn).map {
      value =>
        value.params match {
          case FriendlyAnnoParams.Full(values) =>
            values.toMap.get("name") match {
              case Some(value: FriendlyAnnotationValue.StringValue) =>
                value.value
              case _ =>
                throw new BadIdAnnotationException(value.toString, value)
            }

          case FriendlyAnnoParams.Values(_) =>
            throw new BadIdAnnotationException(value.toString, value)
        }

    }

    lazy val maybeJSRName = parameterSymbol.findUniqueFriendlyAnno(a => a.fqn.endsWith(".Named")).flatMap {
      value =>
        value.params.values match {
          case FriendlyAnnotationValue.StringValue(head) :: Nil =>
            Some(head)
          case _ =>
            None
        }
    }

    maybeDistageName.orElse(maybeJSRName) match {
      case Some(value) => typeKey.named(value)
      case None => typeKey
    }
  }
}
