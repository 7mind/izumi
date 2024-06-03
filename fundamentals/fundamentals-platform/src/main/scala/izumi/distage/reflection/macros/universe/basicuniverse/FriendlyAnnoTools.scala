package izumi.distage.reflection.macros.universe.basicuniverse

import izumi.distage.reflection.macros.universe.basicuniverse

object FriendlyAnnoTools {
  private def convertConst(c: Any): FriendlyAnnotationValue = {
    c match {
      case v: String =>
        FriendlyAnnotationValue.StringValue(v)
      case v: Int =>
        FriendlyAnnotationValue.IntValue(v)
      case v: Long =>
        FriendlyAnnotationValue.LongValue(v)
      case v =>
        FriendlyAnnotationValue.UnknownConst(v)
    }
  }
  def makeFriendly(u: scala.reflect.api.Universe)(anno: u.Annotation): FriendlyAnnotation = {
    import u.*

    val tpe = anno.tree.tpe.finalResultType
    val annoName = tpe.typeSymbol.fullName
    val paramTrees = anno.tree.children.tail

    val avals = if (tpe.typeSymbol.isJavaAnnotation) {
      val pairs = paramTrees.map {
        p =>
          (p: @unchecked) match {
            case NamedArg(Ident(TermName(name)), Literal(Constant(c))) =>
              (Some(name), convertConst(c))
            case a =>
              (None, FriendlyAnnotationValue.UnknownConst(s"$a ${u.showRaw(a)}"))
          }
      }

      val names = pairs.map(_._1).collect { case Some(name) => name }
      val values = pairs.map(_._2)
      assert(names.size >= values.size, s"Java annotation structure disbalance: names=$names values=$values")
      FriendlyAnnoParams.Full(names.zip(values ++ List.fill(names.size - values.size)(FriendlyAnnotationValue.UnsetValue())))
    } else {
      val values = paramTrees.map {
        p =>
          (p: @unchecked) match {
            case Literal(Constant(c)) =>
              convertConst(c)
            case a =>
              FriendlyAnnotationValue.UnknownConst(s"$a ${u.showRaw(a)}")
          }
      }

      val rp = new ConstructorSelector(u)
      val constructor = rp.selectConstructorMethod(tpe.asInstanceOf[rp.u.Type])
      constructor match {
        case Some(c) =>
          c.paramLists match {
            case params :: Nil =>
              val names = params.map(_.name.decodedName.toString)
              assert(names.size >= values.size, s"Annotation structure disbalance: names=$names values=$values")
              FriendlyAnnoParams.Full(names.zip(values ++ List.fill(names.size - values.size)(FriendlyAnnotationValue.UnsetValue())))
            case _ =>
              FriendlyAnnoParams.Values(values)
          }

        case _ =>
          FriendlyAnnoParams.Values(values)
      }
    }

    basicuniverse.FriendlyAnnotation(annoName, avals)
  }
}
