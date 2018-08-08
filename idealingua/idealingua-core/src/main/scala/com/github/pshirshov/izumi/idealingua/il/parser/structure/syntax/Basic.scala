package com.github.pshirshov.izumi.idealingua.il.parser.structure.syntax

import fastparse.all._
import fastparse.CharPredicates._

object Basic {
  val UnicodeEscape = P("u" ~ HexDigit ~ HexDigit ~ HexDigit ~ HexDigit)

  //Numbers and digits

  val digits = "0123456789"
  val Digit = P(CharIn(digits))
  val hexDigits = digits + "abcdefABCDEF"
  val HexDigit = P(CharIn(hexDigits))
  val HexNum = P("0x" ~ CharsWhileIn(hexDigits))
  val DecNum = P(CharsWhileIn(digits))
  val Exp = P(CharIn("Ee") ~ CharIn("+-").? ~ DecNum)
  val FloatType = P(CharIn("fFdD"))

  val WSChars = P(CharsWhileIn("\u0020\u0009"))
  val Newline = P(StringIn("\r\n", "\n"))
  val Semi = P(";" | Newline.rep(1))
  val OpChar = P(CharPred(isOpChar))

  def isOpChar(c: Char) = c match {
    case '!' | '#' | '%' | '&' | '*' | '+' | '-' | '/' |
         ':' | '<' | '=' | '>' | '?' | '@' | '\\' | '^' | '|' | '~' => true
    case _ => isOtherSymbol(c) || isMathSymbol(c)
  }

  val Letter = P(CharPred(c => isLetter(c) | isDigit(c) | c == '$' | c == '_'))
  val LetterDigitDollarUnderscore = P(
    CharPred(c => isLetter(c) | isDigit(c) | c == '$' | c == '_')
  )
  val Lower = P(CharPred(c => isLower(c) || c == '$' | c == '_'))
  val Upper = P(CharPred(isUpper))
}

/**
  * Most keywords don't just require the correct characters to match,
  * they have to ensure that subsequent characters *don't* match in
  * order for it to be a keyword. This enforces that rule for key-words
  * (W) and key-operators (O) which have different non-match criteria.
  */
object Key {
  def W(s: String) = P(s ~ !Basic.LetterDigitDollarUnderscore)(sourcecode.Name(s"`$s`"))

  // If the operator is followed by a comment, stop early so we can parse the comment
  def O(s: String) = P(s ~ (!Basic.OpChar | &("/*" | "//")))(sourcecode.Name(s"`$s`"))
}
