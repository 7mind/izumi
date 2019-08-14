package izumi.idealingua.il.parser.structure

import izumi.idealingua.il.parser.ParserTestTools
import org.scalatest.WordSpec

class CommentsAndDocstringsTest
  extends WordSpec with ParserTestTools {

  "IL parser" should {

    "parse docstrings" in {
      assertParses(comments.DocComment(_),
        """/** docstring
          | */""".stripMargin)

      assertParses(comments.DocComment(_),
        """/** docstring
          |  * docstring
          |  */""".stripMargin)

      assertParses(comments.DocComment(_),
        """/** docstring
          |* docstring
          |*/""".stripMargin)

      assertParses(comments.DocComment(_),
        """/**
          |* docstring
          |*/""".stripMargin)

      assertParses(comments.DocComment(_),
        """/**
          |* docstring
          |*
          |*/""".stripMargin)

      assertParsesInto(comments.DocComment(_),
        """/** docstring
          |  * with *stars*
          |  */""".stripMargin,
        """ docstring
          | with *stars*""".stripMargin
      )
    }


  }
}
