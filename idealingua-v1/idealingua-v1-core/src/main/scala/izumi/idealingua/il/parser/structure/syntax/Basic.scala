package izumi.idealingua.il.parser.structure.syntax

import fastparse._
import fastparse.CharPredicates._
import NoWhitespace._

object Basic {
  def UnicodeEscape[_: P]: P[Unit] = P("u" ~ HexDigit ~ HexDigit ~ HexDigit ~ HexDigit)

  //Numbers and digits

  final val digits  = "0123456789"
  final val hexDigits = "0123456789abcdefABCDEF"

  def Digit[_: P]: P[Unit] = P(CharIn(digits))
  def HexDigit[_: P]: P[Unit] = P(CharIn(hexDigits))
  def HexNum[_: P]: P[Unit] = P("0x" ~ CharsWhileIn(hexDigits))
  def DecNum[_: P]: P[Unit] = P(CharsWhileIn(digits))
  def Exp[_: P]: P[Unit] = P(CharIn("Ee") ~ CharIn("+\\-").? ~ DecNum)
  def FloatType[_: P]: P[Unit] = P(CharIn("fFdD"))

  def WSChars[_: P]: P[Unit] = P(CharsWhileIn("\u0020\u0009"))
  def Newline[_: P]: P[Unit] = P(StringIn("\r\n", "\n"))
  def Semi[_: P]: P[Unit] = P(";" | Newline.rep(1))
  def OpChar[_: P]: P[Unit] = P(CharPred(isOpChar))

  def isOpChar(c: Char): Boolean = c match {
    case '!' | '#' | '%' | '&' | '*' | '+' | '-' | '/' |
         ':' | '<' | '=' | '>' | '?' | '@' | '\\' | '^' | '|' | '~' => true
    case _ => isOtherSymbol(c) || isMathSymbol(c)
  }

  def Letter[_: P]: P[Unit] = P(CharPred(c => isLetter(c) | isDigit(c) | c == '$' | c == '_'))
  def LetterDigitDollarUnderscore[_: P]: P[Unit] = P(
    CharPred(c => isLetter(c) | isDigit(c) | c == '$' | c == '_')
  )
  def Lower[_: P]: P[Unit] = P(CharPred(c => isLower(c) || c == '$' | c == '_'))
  def Upper[_: P]: P[Unit] = P(CharPred(isUpper))
}

/**
  * Most keywords don't just require the correct characters to match,
  * they have to ensure that subsequent characters *don't* match in
  * order for it to be a keyword. This enforces that rule for key-words
  * (W) and key-operators (O) which have different non-match criteria.
  */
object Key {
  def W[_: P](s: String): P[Unit] = P(s ~ !Basic.LetterDigitDollarUnderscore)(sourcecode.Name(s"`$s`"), implicitly)

  // If the operator is followed by a comment, stop early so we can parse the comment
  def O[_: P](s: String): P[Unit] = P(s ~ (!Basic.OpChar | &("/*" | "//")))(sourcecode.Name(s"`$s`"), implicitly)
}
