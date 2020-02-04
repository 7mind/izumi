package izumi.fundamentals.reflection

import java.lang.reflect.Method

import scala.annotation.tailrec
import scala.language.reflectiveCalls
import scala.reflect.api
import scala.reflect.api.{Mirror, TypeCreator, Universe}
import scala.reflect.internal.Symbols
import scala.reflect.macros.blackbox
import scala.reflect.runtime.{universe => ru}
import scala.util.{Failure, Success, Try}

object ReflectionUtil {
  final class MethodMirrorException(message: String, cause: Throwable = null) extends RuntimeException(message, cause)

  def toJavaMethod(definingClass: ru.Type, methodSymbol: ru.Symbol): Method = {
    // https://stackoverflow.com/questions/16787163/get-a-java-lang-reflect-method-from-a-reflect-runtime-universe-methodsymbol
    val method = methodSymbol.asMethod
    definingClass match {
      case r: ru.RefinedTypeApi =>
        throw new MethodMirrorException(
          s"Failed to reflect method: That would require runtime code generation for refined type $definingClass with parents ${r.parents} and scope ${r.decls}")

      case o =>
        toJavaMethod((scala.reflect.runtime.currentMirror: ru.Mirror).runtimeClass(o), method) match {
          case Failure(exception) =>
            throw new MethodMirrorException(s"Failed to reflect method: $methodSymbol in $definingClass", exception)
          case Success(value) =>
            value
        }
    }
  }

  def toJavaMethod(clazz: Class[_], methodSymbol: ru.MethodSymbol): Try[Method] = {
    Try {
      val mirror = ru.runtimeMirror(clazz.getClassLoader)
      val privateMirror = mirror.asInstanceOf[ {
        def methodToJava(sym: Symbols#MethodSymbol): Method
      }]
      val javaMethod = privateMirror.methodToJava(methodSymbol.asInstanceOf[Symbols#MethodSymbol])
      javaMethod
    }
  }

  def typeToTypeTag[T](u: Universe)(tpe: u.Type, mirror: Mirror[u.type]): u.TypeTag[T] = {
    val creator: TypeCreator = new reflect.api.TypeCreator {
      def apply[U <: SingletonUniverse](m: Mirror[U]): U#Type = {
        assert(m eq mirror, s"TypeTag[$tpe] defined in $mirror cannot be migrated to $m.")
        tpe.asInstanceOf[U#Type]
      }
    }

    u.TypeTag(mirror, creator)
  }

  implicit final class WeakTypeTagMigrate[T](private val weakTypeTag: Universe#WeakTypeTag[T]) extends AnyVal {
    def migrate[V <: SingletonUniverse](m: api.Mirror[V]): m.universe.WeakTypeTag[T] = {
      weakTypeTag.in(m).asInstanceOf[m.universe.WeakTypeTag[T]]
    }
  }

  def deannotate[U <: SingletonUniverse](typ: U#Type): U#Type = {
    typ match {
      case t: U#AnnotatedTypeApi =>
        t.underlying
      case _ =>
        typ
    }
  }

  /** Mini `normalize`. `normalize` is deprecated and we don't want to do scary things such as evaluate type-lambdas anyway.
    * And AFAIK the only case that can make us confuse a type-parameter for a non-parameter is an empty refinement `T {}`.
    * So we just strip it when we get it. */
  @tailrec
  final def norm(u: Universe)(x: u.Type): u.Type = {
    import u._
    x match {
      case RefinedType(t :: Nil, m) if m.isEmpty => norm(u)(t)
      case AnnotatedType(_, t) => norm(u)(t)
      case _ => x
    }
  }

  def toTypeRef[U <: SingletonUniverse](tpe: U#TypeApi): Option[U#TypeRefApi] = {
    tpe match {
      case typeRef: U#TypeRefApi =>
        Some(typeRef)
      case _ =>
        None
    }
  }

  def stripByName(u: Universe)(tpe: u.Type): u.Type = {
    if (isByName(u)(tpe)) tpe.typeArgs.head.finalResultType else tpe
  }

  def isByName(u: Universe)(tpe: u.Type): Boolean = {
    tpe.typeSymbol.isClass && tpe.typeSymbol.asClass == u.definitions.ByNameParamClass
  }

  def allPartsStrong(tpe: Universe#Type): Boolean = {
    val selfStrong = isSelfStrong(tpe)
    def prefixStrong = {
      tpe match {
        case t: Universe#TypeRefApi =>
          allPartsStrong(t.pre.dealias)
        case _ =>
          true
      }
    }
    def argsStrong = {
      tpe.dealias.finalResultType.typeArgs.forall {
        arg =>
          tpe.typeParams.contains(arg.typeSymbol) ||
            allPartsStrong(arg)
      }
    }
    def intersectionStructStrong = {
      tpe match {
        case t: Universe#RefinedTypeApi =>
          t.parents.forall(allPartsStrong) &&
            t.decls.toSeq.forall((s: Universe#Symbol) => s.isTerm || allPartsStrong(s.asType.typeSignature.dealias))
        case _ =>
          true
      }
    }

    selfStrong && prefixStrong && argsStrong && intersectionStructStrong
  }

  def isSelfStrong(tpe: Universe#Type): Boolean = {
    !(tpe.typeSymbol.isParameter || (
      tpe.isInstanceOf[Universe#TypeRefApi] &&
        tpe.asInstanceOf[Universe#TypeRefApi].pre.isInstanceOf[Universe#ThisTypeApi] &&
        tpe.typeSymbol.isAbstract && !tpe.typeSymbol.isClass && isNotDealiasedFurther(tpe)
      )) ||
      tpe.typeParams.exists {
        t =>
          t == tpe.typeSymbol ||
            t.typeSignature == tpe.typeSymbol.typeSignature ||
            (t.name eq tpe.typeSymbol.name)
      }
  }

  def isNotDealiasedFurther(tpe: Universe#Type): Boolean = {
    val u: Universe = null
    val tpe1: u.Type = tpe.asInstanceOf[u.Type]
    tpe1.dealias =:= tpe1
  }

  def intersectionTypeMembers[U <: SingletonUniverse](targetType: U#Type): List[U#Type] = {
    def go(tpe: U#Type): List[U#Type] = {
      tpe match {
        case r: U#RefinedTypeApi => r.parents.flatMap(intersectionTypeMembers[U](_: U#Type))
        case _ => List(tpe)
      }
    }
    go(targetType).distinct
  }

  def kindOf(tpe: Universe#Type): Kind = {
    Kind(tpe.typeParams.map(t => kindOf(t.typeSignature)))
  }

  def getStringLiteral(c: blackbox.Context)(tree: c.universe.Tree): String = {
    findStringLiteral(tree).getOrElse(c.abort(c.enclosingPosition, "must use string literal"))
  }

  def findStringLiteral(tree: Universe#Tree): Option[String] = {
    tree.collect {
      case l: Universe#LiteralApi if l.value.value.isInstanceOf[String] =>
        l.value.value.asInstanceOf[String]
    }.headOption
  }

  final case class Kind(args: List[Kind]) {
    def format(typeName: String) = s"$typeName${if (args.nonEmpty) args.mkString("[", ", ", "]") else ""}"
    override def toString: String = format("_")
  }

}

