package izumi.fundamentals.reflection.macrortti

import izumi.thirdparty.internal.boopickle.{PickleImpl, Pickler}
import izumi.fundamentals.platform.console.TrivialLogger
import izumi.fundamentals.reflection.macrortti.LightTypeTag.ParsedLightTypeTag.SubtypeDBs
import izumi.fundamentals.reflection.{DebugProperties, TrivialMacroLogger}

import scala.reflect.macros.blackbox

final class LightTypeTagMacro(override val c: blackbox.Context) extends LightTypeTagMacro0[blackbox.Context](c)

private[reflection] class LightTypeTagMacro0[C <: blackbox.Context](val c: C) {

  import c.universe._

  final val lightTypeTag: Tree = q"${symbolOf[LightTypeTag.type].asClass.module}"

  final private val logger: TrivialLogger = TrivialMacroLogger.make[this.type](c, DebugProperties.`izumi.debug.macro.rtti`)

  protected final def cacheEnabled: Boolean = !c.settings.contains(s"${DebugProperties.`izumi.rtti.cache.compile`}=false")
  protected final val impl = new LightTypeTagImpl[c.universe.type](c.universe, withCache = cacheEnabled, logger)

  @inline final def makeWeakHKTag[ArgStruct: c.WeakTypeTag]: c.Expr[LTag.WeakHK[ArgStruct]] = {
    makeHKTagRaw[ArgStruct](weakTypeOf[ArgStruct])
  }

  @inline final def makeWeakTag[T: c.WeakTypeTag]: c.Expr[LTag.Weak[T]] = {
    val res = makeParsedLightTypeTagImpl(weakTypeOf[T])
    c.Expr[LTag.Weak[T]](q"new ${weakTypeOf[LTag.Weak[T]]}($res)")
  }

  @inline final def makeStrongTag[T: c.WeakTypeTag]: c.Expr[LTag[T]] = {
    val tpe = weakTypeOf[T]
    if (allPartsStrong(tpe)) {
      val res = makeParsedLightTypeTagImpl(tpe)
      c.Expr[LTag[T]](q"new ${weakTypeOf[LTag[T]]}($res)")
    } else {
      c.abort(c.enclosingPosition, s"Can't materialize LTag[$tpe]: found unresolved type parameters in $tpe")
    }
  }

  @inline final def makeParsedLightTypeTag[T: c.WeakTypeTag]: c.Expr[LightTypeTag] = {
    makeParsedLightTypeTagImpl(weakTypeOf[T])
  }

  final def makeHKTagRaw[ArgStruct](argStruct: Type): c.Expr[LTag.WeakHK[ArgStruct]] = {
    def badShapeError(t: TypeApi) = {
      c.abort(
        c.enclosingPosition,
        s"Expected type shape RefinedType `{ type Arg[A] = X[A] }` for summoning `LightTagK[X]`, but got $t (raw: ${showRaw(t)} ${t.getClass})"
      )
    }

    argStruct match {
      case r: RefinedTypeApi =>
        r.decl(TypeName("Arg")) match {
          case sym: TypeSymbolApi =>
            val res = makeParsedLightTypeTagImpl(sym.info.typeConstructor)
            // FIXME: `appliedType` doesn't work here for some reason; have to write down the entire name
            c.Expr[LTag.WeakHK[ArgStruct]](q"new _root_.izumi.fundamentals.reflection.macrortti.LTag.WeakHK[$argStruct]($res)")
          case _ => badShapeError(r)
        }
      case other => badShapeError(other)
    }
  }

  final def makeParsedLightTypeTagImpl(tpe: Type): c.Expr[LightTypeTag] = {
    val res = impl.makeFullTagImpl(tpe)

    @inline def serialize[A: Pickler](a: A): String = {
      val bytes = PickleImpl(a).toByteBuffer.array()
      new String(bytes, 0, bytes.length, "ISO-8859-1")
    }

    val strRef = serialize(res.ref)(LightTypeTag.lttRefSerializer)
    val strDBs = serialize(SubtypeDBs(res.basesdb, res.idb))(LightTypeTag.subtypeDBsSerializer)

    c.Expr[LightTypeTag](q"$lightTypeTag.parse($strRef : _root_.java.lang.String, $strDBs : _root_.java.lang.String)")
  }

  protected final def allPartsStrong(tpe: Type): Boolean = {
    def selfStrong = !tpe.typeSymbol.isParameter
    def prefixStrong = {
      tpe match {
        case t: TypeRefApi =>
          allPartsStrong(t.pre)
        case _ =>
          true
      }
    }
    def argsStrong = tpe.typeArgs.forall(allPartsStrong)
    def intersectionStructStrong = {
      tpe match {
        case t: RefinedTypeApi =>
          t.parents.forall(allPartsStrong) &&
          t.decls.forall(s => s.isTerm || allPartsStrong(s.asType.typeSignature))
        case _ =>
          true
      }
    }
    selfStrong && prefixStrong && argsStrong && intersectionStructStrong
  }
}
