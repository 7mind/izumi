package com.github.pshirshov.izumi.fundamentals.typesafe.config

import com.github.pshirshov.izumi.fundamentals.reflection.SafeType0
import com.typesafe.config._

import scala.annotation.tailrec
import scala.collection.JavaConverters._
//import scala.collection.generic.{GenMapFactory, GenericCompanion}
import scala.collection.immutable.Queue
import scala.collection.{GenMap, GenTraversable}
import scala.reflect.ClassTag
import scala.reflect.runtime.{currentMirror, universe => ru}
import scala.util.{Failure, Try}
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._

class RuntimeConfigReaderDefaultImpl
(
  override val codecs: Map[SafeType0[ru.type], ConfigReader[_]]
) extends RuntimeConfigReader {

  val mirror: ru.Mirror = currentMirror

  /**
    * Opinionated on purpose: Will work ONLY for case classes. Not for sealed traits or other types.
    */
  override def readConfigAsCaseClass(config: Config, tpe: SafeType0[ru.type]): Any = {
    val typesafeConfigRawTypes = Set(
      SafeType0.get[Config]
    , SafeType0.get[ConfigValue]
    , SafeType0.get[ConfigObject]
    , SafeType0.get[ConfigList]
    )
    val maybeReader = if (typesafeConfigRawTypes contains tpe)
      codecs.get(tpe)
    else
      None

    val reader = maybeReader getOrElse deriveCaseClassReader(tpe)

    reader(config.root()).get
  }

  override def readValue(config: ConfigValue, tpe: SafeType0[ru.type]): Any = {
    anyReader(tpe)(config).get
  }

  def anyReader(tpe: SafeType0[ru.type]): ConfigReader[_] = {
    val key = SafeType0(tpe.tpe.dealias.erasure)

    codecs.get(key) match {
      case Some(primitiveValueReader) =>
        primitiveValueReader
      case _ if tpe.tpe.erasure.baseClasses.contains(SafeType0.get[Enum[_]].tpe.erasure.typeSymbol) =>
        val clazz = mirror.runtimeClass(tpe.tpe)

        ConfigReaderInstances.javaEnumReader(ClassTag(clazz))
      case _ if tpe <:< SafeType0.get[collection.Map[String, Any]] =>
        objectMapReader(tpe.tpe.dealias)
      case _ if tpe <:< SafeType0.get[Iterable[Any]] =>
        listReader(tpe.tpe.dealias)(configListReader)
      case _ if key.tpe =:= ru.typeOf[Option[_]].dealias.erasure =>
        optionReader(tpe.tpe.dealias)
      case _ if tupleTypes.exists(_ =:= key.tpe) =>
        listReader(tpe.tpe.dealias)(tupleReader)
      case _ if !tpe.tpe.typeSymbol.isAbstract && tpe.tpe.typeSymbol.isClass && tpe.tpe.typeSymbol.asClass.isCaseClass =>
        deriveCaseClassReader(tpe)
      case _ if tpe.tpe.typeSymbol.isAbstract && tpe.tpe.typeSymbol.isClass && tpe.tpe.typeSymbol.asClass.isSealed =>
        deriveSealedTraitReader(tpe)
      case _ =>
        throw new ConfigReadException(
          s"""Only case classes can be read in config, but type $tpe is a trait or an abstract class.
             | When trying to derive case class reader for $tpe""".stripMargin)
    }
  }

  def deriveCaseClassReader(targetType: SafeType0[ru.type]): ConfigReader[_] =
    cv => Try {
      cv match {
        case obj: ConfigObject =>
          val tpe = targetType.tpe
          val constructorSymbol = tpe.decl(ru.termNames.CONSTRUCTOR).asTerm.alternatives.head.asMethod
          val params = constructorSymbol.typeSignatureIn(tpe).paramLists.flatten.map {
            p =>
              p.name.decodedName.toString -> p.typeSignatureIn(tpe).finalResultType
          }

          val parsedResult = params.foldLeft[Either[Queue[String], Queue[Any]]](Right(Queue.empty)) {
            (e, param) => param match {
              case (name, typ) =>
                val value = obj.get(name)

                val res = anyReader(SafeType0(typ))(value).fold(
                  exc => Left(s"Couldn't parse field `$name` of case class $tpe because of exception: ${exc.getMessage}")
                  , Right(_)
                )

                res.fold(msg => Left(e.left.getOrElse(Queue.empty) :+ msg), v => e.map(_ :+ v))
            }
          }

          val parsedArgs = parsedResult.fold(
            errors => throw new ConfigReadException(
              s"""Couldn't read config object as a case class $targetType, there were errors when trying to parse it's fields from value: $obj
                 |The errors were:
                 |  ${errors.niceList()}
               """.stripMargin
            )
            , identity
          )

          val reflectedClass = mirror.reflectClass(tpe.typeSymbol.asClass)
          val constructor = reflectedClass.reflectConstructor(constructorSymbol)

          constructor.apply(parsedArgs: _*)
        case _ =>
          throw new ConfigReadException(
            s"""
               |Can't read config value as a case class $targetType, config value is a primitive, not an object.
               | ConfigValue was: $cv""".stripMargin)
      }
    }

  def deriveSealedTraitReader(targetType: SafeType0[ru.type]): ConfigReader[_] =
    cv => Try {
      cv.valueType() match {
        case ConfigValueType.OBJECT | ConfigValueType.STRING =>
          import RuntimeConfigReaderDefaultImpl._

          val tpe = targetType.tpe
          val (subclasses, names) = {
            val ctors = ctorsOf(tpe)
            ctors -> ctors.map(c => nameAsString(nameOf(c)))
          }
          val classesWithNames = subclasses zip names

          val objects = classesWithNames.collect {
            case (o, n) if o.typeSymbol.asClass.isModuleClass => o.typeSymbol.asClass.module.asModule -> n
          }

          cv match {
            case _ if cv.valueType() == ConfigValueType.STRING && objects.nonEmpty =>
              val str = anyReader(SafeType0.get[String])(cv).get
              objects.find(_._2 == str) match {
                case Some((o, _)) =>
                  mirror.reflectModule(o).instance
                case None =>
                  throw new ConfigReadException(
                    s"""
                       |Can't read config value as a sealed trait $targetType, string does not match any case object in sealed trait
                       | Expected a string which is a member of enumeration: ${objects.map(_._2).mkString(", ")}
                       | ${if(objects.size == subclasses.size) "" else
                          s"Or an object representing one of case class children of $targetType: ${names.diff(objects.map(_._2)).mkString(", ")}"}
                       | Config value was: $cv
                     """.stripMargin
                  )
              }
            case obj: ConfigObject =>
              if (obj.size() != 1) {
                throw new ConfigReadException(
                  s"""
                     |Can't read config value as a sealed trait $targetType, config object does not have exactly one key!
                     | Expected exactly one key that is one of: ${names.mkString(", ")}
                     | Config value was: $cv
                   """.stripMargin)
              }

              classesWithNames.find {
                case (_, name) => obj.computeIfAbsent(name, _ => null) != null
              } match {
                case Some((typ, name)) =>
                  val value = obj.get(name)

                  anyReader(SafeType0(typ))(value).get
                case None =>
                  throw new ConfigReadException(
                    s"""
                       |Can't read config value as a sealed trait $targetType, config object does not contain a constructor of $targetType!
                       | Expected exactly one key that is one of: ${names.mkString(", ")}
                       | Config value was: $cv
                     """.stripMargin
                  )
              }
          }

        case _ =>
          throw new ConfigReadException(
            s"""
               |Can't read config value as a sealed trait $targetType, config value is a primitive, not an object.
               | ConfigValue was: $cv""".stripMargin)
      }
    }

  def objectMapReader(mapType: ru.Type): ConfigReader[GenMap[String, _]] = {
    case co: ConfigObject => Try {
      val tyParam = SafeType0(mapType.dealias.typeArgs.last)

      ???
//      mirror.reflectModule(mapType.dealias.companion.typeSymbol.asClass.module.asModule).instance match {
//        case companionFactory: GenMapFactory[GenMap] @unchecked =>
//
//          val map = co.asScala
//
//          val parsedResult = map.foldLeft[Either[Queue[String], Queue[(String, Any)]]](Right(Queue.empty)) {
//            (e, kv) => kv match {
//              case (key, value) =>
//                val res = anyReader(tyParam)(value).fold(
//                  exc => Left(s"Couldn't parse key `$key` because of exception: ${exc.getMessage}")
//                  , Right(_)
//                )
//
//                res.fold(msg => Left(e.left.getOrElse(Queue.empty) :+ msg), v => e.map(_ :+ (key -> v)))
//            }
//          }
//
//          val parsedArgs = parsedResult.fold(
//            errors => throw new ConfigReadException(
//              s"""Couldn't read config object as Map type $mapType, there were errors when trying to parse it's fields from value: $co
//                 |The errors were:
//                 |  ${errors.niceList()}
//               """.stripMargin
//            )
//            , identity
//          )
//
//          val res = companionFactory.newBuilder.++=(parsedArgs).result()
//          res
//        case c =>
//          throw new ConfigReadException(
//            s"""When trying to read a Map type $mapType: can't instantiate class. Expected a companion object of type
//               | scala.collection.generic.GenMapFactory to be present, but $mapType companion object has type: ${c.getClass}.
//               |
//               | ConfigValue was: $co""".stripMargin)
//      }
    }
    case cv =>
      Failure(new ConfigReadException(
        s"""Can't read config value as a map $mapType, config value is not an object.
           | ConfigValue was: $cv""".stripMargin))
  }

  def listReader[T](listType: ru.Type)(reader: (ru.Type, ConfigList) => Try[T]): ConfigReader[T] = {
    case cl: ConfigList =>
      reader(listType, cl)

    case cl: ConfigObject if isList(cl) =>
      val asList = ConfigValueFactory.fromIterable(cl.unwrapped().values())
      reader(listType, asList)

    case cv =>
      Failure(new ConfigReadException(
        s"""Can't read config value as a list $listType, config value is not a list.
           | ConfigValue was: $cv""".stripMargin))
  }

  private[this] def configListReader(listType: ru.Type, cl: ConfigList): Try[GenTraversable[_]] = {
    Try {
      val tyParam = SafeType0(listType.dealias.typeArgs.last)

      ???
//      mirror.reflectModule(listType.dealias.companion.typeSymbol.asClass.module.asModule).instance match {
//        case companionFactory: GenericCompanion[GenTraversable]@unchecked =>
//          val list = cl.asScala
//
//          val parsedResult = list.zipWithIndex.foldLeft[Either[Queue[String], Queue[Any]]](Right(Queue.empty)) {
//            (e, configValue) => configValue match {
//              case (value, idx) =>
//                val res = anyReader(tyParam)(value).fold(
//                  exc => Left(s"Couldn't parse value at index `${idx+1}` of a list $listType because of exception: ${exc.getMessage}")
//                  , Right(_)
//                )
//
//                res.fold(msg => Left(e.left.getOrElse(Queue.empty) :+ msg), v => e.map(_ :+ v))
//            }
//          }
//
//          val parsedArgs = parsedResult.fold(
//            errors => throw new ConfigReadException(
//              s"""Couldn't read config list as a collectionType $listType, there were errors when trying to parse it's fields from value: $cl
//                 |The errors were:
//                 |  ${errors.niceList()}
//               """.stripMargin
//            )
//            , identity
//          )
//
//          companionFactory(parsedArgs: _*)
//        case c =>
//          throw new ConfigReadException(
//            s"""When trying to read a collection type $listType: can't instantiate class. Expected a companion object of type
//               | scala.collection.generic.GenericCompanion to be present, but $listType companion object has type: ${c.getClass}.
//               |
//               | ConfigValue was: $cl""".stripMargin)
//      }
    }
  }

  def isList(configObject: ConfigObject): Boolean = {
    configObject.unwrapped().keySet().asScala.forall(_.forall(_.isDigit))
  }

  def optionReader(optionType: ru.Type): ConfigReader[Option[_]] = {
    cv => Try {
      if (cv == null || cv.valueType == ConfigValueType.NULL) {
        None
      } else {
        val tyParam = SafeType0(optionType.typeArgs.head)
        Option(anyReader(tyParam)(cv).get)
      }
    }
  }

  def tupleReader(tpe: ru.Type, cl: ConfigList): Try[_] = Try {
    val constructorSymbol = tpe.decl(ru.termNames.CONSTRUCTOR).asTerm.alternatives.head.asMethod
    val params = constructorSymbol.typeSignatureIn(tpe).paramLists.flatten.map {
      _.typeSignatureIn(tpe).finalResultType
    }.zipWithIndex

    val parsedResult = params.foldLeft[Either[Queue[String], Queue[Any]]](Right(Queue.empty)) {
      (e, param) => param match {
        case (typ, idx) =>

          val value = Try(cl.get(idx))

          val res = value.flatMap(anyReader(SafeType0(typ))(_)).fold(
            exc =>
              Left(s"Couldn't parse parameter #$idx of a tuple $tpe because of exception: ${exc.getMessage}")
            , Right(_)
          )

          res.fold(msg => Left(e.left.getOrElse(Queue.empty) :+ msg), v => e.map(_ :+ v))
      }
    }

    val parsedArgs = parsedResult.fold(
      errors => throw new ConfigReadException(
        s"""Couldn't read config object as a tuple $tpe, there were errors when trying to parse it's fields from value: $cl
           |The errors were:
           |  ${errors.niceList()}
         """.stripMargin
      )
      , identity
    )

    val reflectedClass = mirror.reflectClass(tpe.typeSymbol.asClass)
    val constructor = reflectedClass.reflectConstructor(constructorSymbol)

    constructor.apply(parsedArgs: _*)
  }

  private[this] val tupleTypes: Seq[ru.Type] = Seq(
    SafeType0.get[Tuple1[_]].tpe.erasure
  , SafeType0.get[Tuple2[_, _]].tpe.erasure
  , SafeType0.get[Tuple3[_, _, _]].tpe.erasure
  , SafeType0.get[Tuple4[_, _, _, _]].tpe.erasure
  , SafeType0.get[Tuple5[_, _, _, _, _]].tpe.erasure
  , SafeType0.get[Tuple6[_, _, _, _, _, _]].tpe.erasure
  , SafeType0.get[Tuple7[_, _, _, _, _, _, _]].tpe.erasure
  , SafeType0.get[Tuple8[_, _, _, _, _, _, _, _]].tpe.erasure
  , SafeType0.get[Tuple9[_, _, _, _, _, _, _, _, _]].tpe.erasure
  , SafeType0.get[Tuple10[_, _, _, _, _, _, _, _, _, _]].tpe.erasure
  , SafeType0.get[Tuple11[_, _, _, _, _, _, _, _, _, _, _]].tpe.erasure
  , SafeType0.get[Tuple12[_, _, _, _, _, _, _, _, _, _, _, _]].tpe.erasure
  , SafeType0.get[Tuple13[_, _, _, _, _, _, _, _, _, _, _, _, _]].tpe.erasure
  , SafeType0.get[Tuple14[_, _, _, _, _, _, _, _, _, _, _, _, _, _]].tpe.erasure
  , SafeType0.get[Tuple15[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _]].tpe.erasure
  , SafeType0.get[Tuple16[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]].tpe.erasure
  , SafeType0.get[Tuple17[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]].tpe.erasure
  , SafeType0.get[Tuple18[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]].tpe.erasure
  , SafeType0.get[Tuple19[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]].tpe.erasure
  , SafeType0.get[Tuple20[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]].tpe.erasure
  , SafeType0.get[Tuple21[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]].tpe.erasure
  , SafeType0.get[Tuple22[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]].tpe.erasure
  )
}

object RuntimeConfigReaderDefaultImpl {
  def apply(codecs: Set[RuntimeConfigReaderCodecs]): RuntimeConfigReaderDefaultImpl =
    new RuntimeConfigReaderDefaultImpl(codecs.map(_.readerCodecs).reduceOption(_ ++ _) getOrElse Map.empty)

  // Copypasta from shapeless-2.3.3 generic.scala macro adapted for runtime.universe
  // https://github.com/milessabin/shapeless/blob/a3113b385959278f373dda2e5231048df2967b57/core/src/main/scala/shapeless/generic.scala#L371

  import scala.reflect.runtime.universe._

  private[RuntimeConfigReaderDefaultImpl] def ctorsOf(tpe: Type): List[Type] = distinctCtorsOfAux(tpe, false)
  private[RuntimeConfigReaderDefaultImpl] def nameAsString(name: Name): String = name.decodedName.toString.trim
  private[RuntimeConfigReaderDefaultImpl] def nameOf(tpe: Type) = tpe.typeSymbol.name


  private[this] def abort(s: String): Nothing =
    throw new ConfigReadException(s"Couldn't determine trait subclasses: $s")

  private[this] def distinctCtorsOfAux(tpe: Type, hk: Boolean): List[Type] = {
    def distinct[A](list: List[A])(eq: (A, A) => Boolean): List[A] = list.foldLeft(List.empty[A]) { (acc, x) =>
        if (!acc.exists(eq(x, _))) x :: acc
        else acc
    }.reverse
    distinct(ctorsOfAux(tpe, hk))(_ =:= _)
  }

  private[this] def ctorsOfAux(tpe: Type, hk: Boolean): List[Type] = {
    def collectCtors(classSym: ClassSymbol): List[ClassSymbol] = {
      classSym.knownDirectSubclasses.toList flatMap { child0 =>
        val child = child0.asClass
        child.typeSignature // Workaround for <https://issues.scala-lang.org/browse/SI-7755>
        if (isCaseClassLike(child) || isCaseObjectLike(child))
          List(child)
        else if (child.isSealed)
          collectCtors(child)
        else
          abort(s"$child is not case class like or a sealed trait")
      }
    }

    val basePre = prefix(tpe)
    val baseSym = classSym(tpe)
    val baseTpe =
      if(!hk) tpe
      else {
        val tc = tpe.typeConstructor
        val paramSym = tc.typeParams.head
        val paramTpe = paramSym.asType.toType
        appliedType(tc, paramTpe)
      }
    val baseArgs = baseTpe.dealias.typeArgs

    val ctorSyms = collectCtors(baseSym).sortBy(_.fullName)
    val ctors =
      ctorSyms flatMap { sym =>
        def normalizeTermName(name: Name): TermName =
          TermName(name.toString.stripSuffix(" "))

        def substituteArgs: List[Type] = {
          val subst = internal.thisType(sym).baseType(baseSym).typeArgs
          sym.typeParams.map { param =>
            val paramTpe = param.asType.toType
            baseArgs(subst.indexWhere(_.typeSymbol == paramTpe.typeSymbol))
          }
        }

        val suffix = ownerChain(sym).dropWhile(_ != basePre.typeSymbol)
        val ctor =
          if(suffix.isEmpty) {
            if(sym.isModuleClass) {
              val moduleSym = sym.asClass.module
              val modulePre = prefix(moduleSym.typeSignatureIn(basePre))
              internal.singleType(modulePre, moduleSym)
            } else
              appliedType(sym.toTypeIn(basePre), substituteArgs)
          } else {
            if(sym.isModuleClass) {
              val path = suffix.tail.map(sym => normalizeTermName(sym.name))
              val (modulePre, moduleSym) = mkDependentRef(basePre, path)
              internal.singleType(modulePre, moduleSym)
            } else if(isAnonOrRefinement(sym)) {
              val path = suffix.tail.init.map(sym => normalizeTermName(sym.name))
              val (valPre, valSym) = mkDependentRef(basePre, path)
              internal.singleType(valPre, valSym)
            } else {
              val path = suffix.tail.init.map(sym => normalizeTermName(sym.name)) :+ suffix.last.name.toTypeName
              val (subTpePre, subTpeSym) = mkDependentRef(basePre, path)
              internal.typeRef(subTpePre, subTpeSym, substituteArgs)
            }
          }
        if(ctor <:< baseTpe) Some(ctor) else None
      }
    if (ctors.isEmpty)
      abort(s"Sealed trait $tpe has no case class subtypes")
    ctors
  }

  private[this] def classSym(tpe: Type): ClassSymbol = {
    val sym = tpe.typeSymbol
    if (!sym.isClass)
      abort(s"$sym is not a class or trait")

    val classSym = sym.asClass
    classSym.typeSignature // Workaround for <https://issues.scala-lang.org/browse/SI-7755>

    classSym
  }

  private[this] def prefix(tpe: Type): Type = {
    val gTpe = tpe.asInstanceOf[scala.reflect.internal.Types#Type]
    gTpe.prefix.asInstanceOf[Type]
  }

  private[this] def isCaseClassLike(sym: ClassSymbol): Boolean = {
    def checkCtor: Boolean = {
      def unique[T](s: Seq[T]): Option[T] =
        s.headOption.find(_ => s.tail.isEmpty)

      val tpe = sym.typeSignature
      (for {
        ctor <- accessiblePrimaryCtorOf(tpe)
        params <- unique(ctor.asMethod.paramLists)
      } yield params.size == fieldsOf(tpe).size).getOrElse(false)
    }

    sym.isCaseClass ||
    (!sym.isAbstract && !sym.isTrait && !(sym == symbolOf[Object]) &&
     sym.knownDirectSubclasses.isEmpty && checkCtor)
  }

  private[this] def isCaseObjectLike(sym: ClassSymbol): Boolean = sym.isModuleClass

  private[this] def ownerChain(sym: Symbol): List[Symbol] = {
    @tailrec
    def loop(sym: Symbol, acc: List[Symbol]): List[Symbol] =
      if(sym.owner == NoSymbol) acc
      else loop(sym.owner, sym :: acc)

    loop(sym, Nil)
  }

  private[this] def accessiblePrimaryCtorOf(tpe: Type): Option[Symbol] = {
    for {
      ctor <- tpe.decls.find { sym => sym.isMethod && sym.asMethod.isPrimaryConstructor }
      if !ctor.isJava || productCtorsOf(tpe).size == 1
    } yield ctor
  }

  private[this] def productCtorsOf(tpe: Type): List[Symbol] = tpe.decls.toList.filter(_.isConstructor)

  private[this] def fieldsOf(tpe: Type): List[(TermName, Type)] = {
    val tSym = tpe.typeSymbol
    if(tSym.isClass && isAnonOrRefinement(tSym)) Nil
    else
      tpe.decls.sorted collect {
        case sym: TermSymbol if isCaseAccessorLike(sym) =>
          (sym.name.toTermName, sym.typeSignatureIn(tpe).finalResultType)
      }
  }

  private[this] def isAnonOrRefinement(sym: Symbol): Boolean = {
    val nameStr = sym.name.toString
    nameStr.contains("$anon") || nameStr == "<refinement>"
  }

  private[this] def isCaseAccessorLike(sym: TermSymbol): Boolean = {
    def isGetter = if (sym.owner.asClass.isCaseClass) sym.isCaseAccessor else sym.isGetter
    sym.isPublic && isGetter && !isNonGeneric(sym)
  }

  private[this] def isNonGeneric(sym: Symbol): Boolean = {
    // See https://issues.scala-lang.org/browse/SI-7561
    sym.isTerm && sym.asTerm.isAccessor || sym.overrides.exists(isNonGeneric)
  }

  private[this] def mkDependentRef(prefix: Type, path: List[Name]): (Type, Symbol) = {
    val (_, pre, sym) =
      path.foldLeft((prefix, NoType, NoSymbol)) {
        case ((pre, _, _), nme) =>
          val sym0 = pre.member(nme)
          val pre0 = sym0.typeSignature
          (pre0, pre, sym0)
      }
    (pre, sym)
  }

}
