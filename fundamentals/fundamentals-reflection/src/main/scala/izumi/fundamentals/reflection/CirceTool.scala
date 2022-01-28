package izumi.fundamentals.reflection

import scala.collection.mutable
import scala.language.experimental.macros
import scala.reflect.macros.blackbox

object CirceTool {
  def make[T](): Unit = macro CirceToolMacro.make[T]
}

class CirceToolMacro(val c: blackbox.Context) {
  import c.universe._

  @inline def make[T: c.WeakTypeTag](): c.Expr[Unit] = {
    val all = new mutable.HashSet[Type]()
    val base = weakTypeOf[T]
    processType(base, all)

    val allSymbols = all.map(_.typeSymbol)

    println(s"/* -- BEGIN: $base -- */")
    val x = all.toSeq
      .filterNot(t => t.toString.startsWith("scala") || t.toString.startsWith("java") || !t.toString.contains("."))
      .filter(t => t.typeSymbol.isClass)
      .sortBy {
        t =>
          val kind = if (t.typeSymbol.asClass.isTrait) {
            if (complexPoly(t)) {
              0
            } else {
              1
            }
          } else {
            2
          }
          (kind, -t.baseClasses.size, t.toString)
      }
      .map {
        t =>
          if (t.typeSymbol.asClass.isTrait) {
            val bad = t.typeSymbol.asClass.baseClasses.filter(b => (b ne t.typeSymbol) && allSymbols.contains(b))
            val isSealedSubHier = bad.nonEmpty
            if (isSealedSubHier) {
              s"implicit def `codec:$t`: Codec.AsObject[$t] = io.circe.generic.semiauto.deriveCodec"
            } else if (complexPoly(t)) {
              s"implicit def `codec:$t`: Codec.AsObject[$t] = io.circe.generic.semiauto.deriveCodec"
            } else {
              s"implicit def `codec:$t`: Codec.AsObject[$t] = io.circe.derivation.deriveCodec"
            }
          } else {
            s"implicit def `codec:$t`: Codec.AsObject[$t] = io.circe.derivation.deriveCodec"
          }

      }
      .distinct

    println(x.filter(_.nonEmpty).mkString("\n"))
    println(s"/* -- END: $base -- */")

    c.Expr[Unit](q"()")
  }

  private def complexPoly[T: c.WeakTypeTag](t: c.universe.Type) = {
    t.typeSymbol.asClass.knownDirectSubclasses.exists(_.asClass.isTrait)
  }

  def processType(t0: Type, all: mutable.HashSet[Type]): Unit = {
    val t = t0.dealias
    if (t.typeArgs.isEmpty) {
      handleNonGeneric(t, all)
    } else {
      t.typeArgs.foreach(a => processType(a, all))
    }

    //m.map(_.asMethod).foreach(m => println((m.name, )))

  }

  private def handleNonGeneric(t0: c.universe.Type, all: mutable.HashSet[c.universe.Type]): Unit = {
    val t = t0.dealias
    if (!all.contains(t)) {
      all.add(t)

      // this triggers full typing so  knownDirectSubclasses works
      if (t.toString == "" || t.typeSymbol.toString == "") {
        ???
      }

      if (t.typeSymbol.isClass) {
        if (t.typeSymbol.asClass.knownDirectSubclasses.isEmpty) {
          val methods = t.members.filter(m => m.isMethod && m.asMethod.isGetter).map(_.asMethod)
          methods.foreach(m => processType(m.returnType, all))

          //        println(("?", Modifiers(t.finalResultType.typeSymbol.asInstanceOf[{def flags: FlagSet}].flags).hasFlag(Flag.TRAIT) ))
        } else {
          t.typeSymbol.asClass.knownDirectSubclasses.foreach {
            s =>
              if (s.isType) {
                processType(s.asType.toType, all)
              } else {
                println(("Not a type?..", s))
              }
          }
        }
      } else {
        println(("Not a class nor trait?..", t))

      }

    }
  }
}
