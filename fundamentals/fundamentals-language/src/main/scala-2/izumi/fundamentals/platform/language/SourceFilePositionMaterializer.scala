package izumi.fundamentals.platform.language

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

final case class SourceFilePositionMaterializer(get: SourceFilePosition) extends AnyVal

object SourceFilePositionMaterializer {
  @inline def sourcePosition(implicit ev: SourceFilePositionMaterializer): SourceFilePosition = ev.get

  implicit def materialize: SourceFilePositionMaterializer = macro SourcePositionMaterializerMacro.getSourceFilePositionMaterializer

  object SourcePositionMaterializerMacro {

    // scalactic.source.Position does all that manually and uses `setType`s to avoid retypechecking, oh well might as well...
    // PS: To ensure that retypechecking does not happen, put a known bad type into `setType` and watch compiler crash.
    //     If it _doesn't_ crash, that means one of the tree nodes was missing a `setType`

    def literal(c: blackbox.Context)(tpe: c.Type)(a: Any): c.universe.Literal = {
      c.internal.setType(c.universe.Literal(c.universe.Constant(a)), tpe)
    }

    def getSourceFilePositionMaterializer(c: blackbox.Context): c.Tree = {
      import c.universe._
      import c.universe.internal._
      import c.universe.internal.gen.{mkAttributedIdent, mkAttributedSelect}

      val sourceFilePosition = getSourceFilePosition(c)

      val matTpe = typeOf[SourceFilePositionMaterializer]
      val matModule = matTpe.companion.typeSymbol.asClass.module

      val sourceFilePositionMaterliazer = Apply(
        mkAttributedSelect(
          mkAttributedIdent(matModule),
          matModule.typeSignature.decl(TermName("apply")),
        ),
        sourceFilePosition :: Nil,
      )
      setType(sourceFilePositionMaterliazer, matTpe)
    }

    def getSourceFilePosition(c: blackbox.Context): c.Tree = {
      import c.universe._
      import c.universe.internal._
      import c.universe.internal.gen.{mkAttributedIdent, mkAttributedSelect}

      val posTpe = typeOf[SourceFilePosition]
      val posModule = posTpe.companion.typeSymbol.asClass.module

      val sourceFilePosition = Apply(
        mkAttributedSelect(
          mkAttributedIdent(posModule),
          posModule.typeSignature.decl(TermName("apply")),
        ),
        literal(c)(definitions.StringClass.toTypeConstructor)(c.enclosingPosition.source.file.name) ::
        literal(c)(definitions.IntTpe)(c.enclosingPosition.line) :: Nil,
      )
      setType(sourceFilePosition, posTpe)
    }

    def getSourceFilePositionValue(c: blackbox.Context): SourceFilePosition = {
      SourceFilePosition(c.enclosingPosition.source.file.name, c.enclosingPosition.line)
    }

  }

}
