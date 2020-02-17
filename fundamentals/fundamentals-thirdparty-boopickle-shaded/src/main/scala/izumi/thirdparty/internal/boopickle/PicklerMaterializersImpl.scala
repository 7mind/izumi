package izumi.thirdparty.internal.boopickle

import scala.reflect.macros.blackbox

private[izumi] object PicklerMaterializersImpl {

  private val logmacro = {
    val prop = System.getProperty("boopickle.logmacro")
    prop != null && prop.toLowerCase == "true"
  }
  private var stats: Map[String, Int] = Map()
  private var overallCount            = 0
  private def logStatistics(x: String): Unit = {
    stats += ((x, 1 + stats.getOrElse(x, 0)))
    overallCount += 1
    var count = stats(x)
    if (count == 1) {
      println(s"Boopickle macro: overall: $overallCount, class: $x")
    } else {
      println(s"Boopickle macro: overall: $overallCount, counter: $count, generating: $x")
    }
  }

  private def pickleSealedTrait(c: blackbox.Context)(tpe: c.universe.Type): c.universe.Tree = {
    import c.universe._

    val concreteTypes = findConcreteTypes(c)(tpe)
    val name          = TermName(c.freshName("TraitPickler"))

    q"""
      implicit object $name extends _root_.izumi.thirdparty.internal.boopickle.CompositePickler[$tpe] {
        ..$concreteTypes
      }
      $name
    """
  }

  private def findConcreteTypes(c: blackbox.Context)(tpe: c.universe.Type): Seq[c.universe.Tree] = {
    import c.universe._

    val sym = tpe.typeSymbol.asClass
    // must be a sealed trait
    if (!sym.isSealed) {
      val msg = s"The referenced trait ${sym.name} must be sealed. For non-sealed traits, create a pickler " +
        "with boopickle.CompositePickler. You may also get this error if a pickler for a class in your type hierarchy cannot be found."
      c.abort(c.enclosingPosition, msg)
    }

    if (sym.knownDirectSubclasses.isEmpty) {
      val msg = s"The referenced trait ${sym.name} does not have any sub-classes. This may " +
        "happen due to a limitation of scalac (SI-7046) given that the trait is " +
        "not in the same package. If this is the case, the pickler may be " +
        "defined using boopickle.CompositePickler directly."
      c.abort(c.enclosingPosition, msg)
    }

    // find all implementation classes in the trait hierarchy
    def findSubClasses(p: c.universe.ClassSymbol): Set[c.universe.ClassSymbol] = {
      p.knownDirectSubclasses.flatMap { sub =>
        val subClass = sub.asClass
        if (subClass.isTrait)
          findSubClasses(subClass)
        else
          Set(subClass) ++ findSubClasses(subClass)
      }
    }
    // sort class names to make sure they are always in the same order
    val result = findSubClasses(sym).toSeq.sortBy(_.name.toString).map { s =>
      if (s.typeParams.isEmpty) {
        q"""addConcreteType[$s]"""
      } else {
        val t = unifyClassWithTrait(c)(tpe, s)
        q"""addConcreteType[$t]"""
      }
    }
    result
  }

  private def pickleValueClass(c: blackbox.Context)(tpe: c.universe.Type): c.universe.Tree = {
    import c.universe._

    val parameter = tpe.typeSymbol.asClass.primaryConstructor
      .typeSignatureIn(tpe)
      .paramLists
      .head
      .head

    val parameterType = parameter.typeSignature
    val parameterTerm = parameter.name.toTermName

    q"""
      _root_.izumi.thirdparty.internal.boopickle.Default.transformPickler[$tpe, $parameterType](v => new $tpe(v))(v => v.$parameterTerm)
    """
  }

  def materializePickler[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[Pickler[T]] = {
    import c.universe._

    val tpe = weakTypeOf[T]

    if (logmacro) {
      logStatistics(tpe.toString)
    }

    if (!tpe.typeSymbol.isClass)
      throw new RuntimeException(s"Enclosure: ${c.enclosingPosition.toString}, type = $tpe")

    val sym = tpe.typeSymbol.asClass

    // special handling of sealed traits
    if ((sym.isTrait || sym.isAbstract) && !sym.fullName.toString.startsWith("scala")) {
      return c.Expr[Pickler[T]](pickleSealedTrait(c)(tpe))
    }

    // special handling of value classes
    if (sym.isDerivedValueClass) {
      return c.Expr[Pickler[T]](pickleValueClass(c)(tpe))
    }

    if (!sym.isCaseClass) {
      c.error(
        c.enclosingPosition,
        s"Cannot materialize pickler for non-case class: $tpe. If this is a collection, the error can refer to the class inside.")
      return c.Expr[Pickler[T]](q"null")
    }

    val pickleLogic = if (sym.isModuleClass) {
      // no need to write anything for case objects
      q"""()"""
    } else {
      val accessors = (tpe.decls collect {
        case acc: MethodSymbol if acc.isCaseAccessor => acc
      }).toList

      val pickleFields = for {
        accessor <- accessors
      } yield {
        val fieldTpe = accessor.typeSignatureIn(tpe).finalResultType
        q"""state.pickle[$fieldTpe](value.${accessor.name})"""
      }

      q"""
        val ref = state.identityRefFor(value)
        if(ref.isDefined) {
          state.enc.writeInt(-ref.get)
        } else {
          state.enc.writeInt(0)
          ..$pickleFields
          state.addIdentityRef(value)
        }
      """
    }

    val unpickleLogic = if (sym.isModuleClass) {
      c.parse(sym.fullName)
    } else {
      val accessors = tpe.decls.collect {
        case acc: MethodSymbol if acc.isCaseAccessor => acc
      }.toList

      val unpickledFields = for {
        accessor <- accessors
      } yield {
        val fieldTpe = accessor.typeSignatureIn(tpe).finalResultType
        q"""state.unpickle[$fieldTpe]"""
      }
      q"""
        val ic = state.dec.readInt
        if(ic == 0) {
          val value = new $tpe(..$unpickledFields)
          state.addIdentityRef(value)
          value
        } else if(ic < 0) {
          state.identityFor[$tpe](-ic)
        } else {
          state.codingError(ic)
        }
      """
    }

    val name = TermName(c.freshName("Pickler"))

    val result = q"""
      implicit object $name extends _root_.izumi.thirdparty.internal.boopickle.Pickler[$tpe] {
        override def pickle(value: $tpe)(implicit state: _root_.izumi.thirdparty.internal.boopickle.PickleState): Unit = { $pickleLogic; () }
        override def unpickle(implicit state: _root_.izumi.thirdparty.internal.boopickle.UnpickleState): $tpe = $unpickleLogic
      }
      $name
    """

    c.Expr[Pickler[T]](result)
  }

  private def unifyClassWithTrait(c: blackbox.Context)(ttrait: c.universe.Type, classSym: c.universe.ClassSymbol) = {

    val tclass             = classSym.toType
    val traitSeenFromClass = tclass.baseType(ttrait.typeSymbol)

    tclass.substituteTypes(traitSeenFromClass.typeArgs.map(_.typeSymbol), ttrait.typeArgs)
  }
}
