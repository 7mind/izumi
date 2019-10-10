package izumi.fundamentals.reflection

import scala.collection.mutable
import scala.language.experimental.macros
import scala.reflect.internal.Trees
import scala.reflect.macros.blackbox

object CirceTool {
  def make[T](): Unit = macro CirceToolMacro.make[T]
}

class CirceToolMacro(val c: blackbox.Context) {

  import c.universe._

  @inline def make[T: c.WeakTypeTag](): c.Expr[Unit] = {
    val all = new mutable.HashSet[Type]()
    processType(weakTypeOf[T], all)
    println("===")
    val x = all
      .toSeq
      .filterNot(t => t.toString.startsWith("scala") || t.toString.startsWith("java"))
      .map {
        t =>
          s"implicit def `codec:${t}`: Codec[$t] = deriveCodec"
      }
      .distinct

    println(x.sorted.mkString("\n"))

    reify(())
  }

  def processType(t: Type, all: mutable.HashSet[Type]): Unit = {

    if (t.typeArgs.isEmpty) {


      handleNonGeneric(t, all)
    } else {
      t.typeArgs.foreach(a => handleNonGeneric(a, all))
    }



    //m.map(_.asMethod).foreach(m => println((m.name, )))

  }

  private def handleNonGeneric(t: c.universe.Type, all: mutable.HashSet[c.universe.Type]): Unit = {
    if (!all.contains(t)) {
      all.add(t)

      val x = c.universe.asInstanceOf[Trees]

      //println(("...", t, t.typeSymbol, t.typeSymbol.asClass, t.typeSymbol.asClass.isSealed, t.typeSymbol.asClass.isTrait, t.typeSymbol.asClass.isCaseClass)) //, t.typeSymbol.asClass, t.typeSymbol.isClass, t.typeSymbol.asClass.isCaseClass, t.typeSymbol.asClass.isSealed))
      //println(("...", t, t.typeSymbol, t, ))

      //println((".", t, t.typeSymbol, t.typeSymbol.asClass.isTrait))
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
                println(("?", s))
              }
          }
        }
      } else {
        println(("??", t))

      }


      //      if (t.typeSymbol.asClass.isCaseClass) {
      //
      //      } else if (t.typeSymbol.asClass.isTrait) {
      //        println(("!", t))
      //
      //      } else {
      //        println(("???", t))
      //
      //      }

    }
  }
}
