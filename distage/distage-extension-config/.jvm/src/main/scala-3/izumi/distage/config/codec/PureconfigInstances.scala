package izumi.distage.config.codec

import com.typesafe.config.{ConfigRenderOptions, ConfigValue}
import pureconfig.*
import pureconfig.error.*
import pureconfig.generic.derivation.Utils

import scala.compiletime.ops.int.+
import scala.compiletime.{constValue, erasedValue, summonFrom}
import scala.deriving.Mirror
import scala.util.chaining.*

object PureconfigInstances {

  object auto {
    inline implicit def exportDerivedConfigReader[A]: Exported[ConfigReader[A]] = {
      summonFrom {
        case c: ConfigReader[A] => Exported(c) // avoids `increase -Xmax-inlines` error, but makes no sense to me. Shouldn't be required at all.
        case m: Mirror.Of[A] =>
          Exported(configReaderDerivation.derived[A](using m))
      }
    }
  }

  object configReaderDerivation {

    inline def derived[A](using m: Mirror.Of[A]): ConfigReader[A] = {
      inline m match {
        case given Mirror.ProductOf[A] => derivedProduct
        case given Mirror.SumOf[A] => derivedSum
      }
    }

    /** Override pureconfig's default `kebab-case` fields – force CamelCase product-hint */
    inline def derivedProduct[A](using m: Mirror.ProductOf[A]): ConfigReader[A] = {
      inline erasedValue[A] match {
        case _: Tuple =>
          new ConfigReader[A] {
            override def from(cur: ConfigCursor): ConfigReader.Result[A] =
              for {
                listCur <- asList(cur)
                result <- readTuple[A & Tuple, 0](listCur.list.toArray, Array.empty)
              } yield result

            def asList(cur: ConfigCursor): Either[ConfigReaderFailures, ConfigListCursor] =
              cur.asListCursor.flatMap {
                listCur =>
                  if (constValue[Tuple.Size[A & Tuple]] == listCur.size)
                    Right(listCur)
                  else
                    listCur.failed(
                      WrongSizeList(constValue[Tuple.Size[A & Tuple]], listCur.size)
                    )
              }
          }

        case _ =>
          new ConfigReader[A] {
            def from(cur: ConfigCursor): ConfigReader.Result[A] =
              for {
                objCur <- cur.asObjectCursor
                result <-
                  Utils
                    .transformedLabels[A](fieldMapping)
                    .toArray
                    .pipe(
                      labels =>
                        readTuple[m.MirroredElemTypes, 0](
                          labels.map(objCur.atKeyOrUndefined(_)),
                          labels.map(KeyNotFound.forKeys(_, objCur.keys)),
                        )
                    )
              } yield m.fromProduct(result)
          }
      }
    }

    inline def readTuple[T <: Tuple, N <: Int](
      cursors: Array[ConfigCursor],
      keyNotFound: Array[KeyNotFound],
    ): Either[ConfigReaderFailures, T] =
      inline erasedValue[T] match {
        case _: (h *: t) =>
          val reader = summonConfigReader[h]
          val cursor = cursors(constValue[N])
          val h =
            (reader.isInstanceOf[ReadsMissingKeys], cursor.isUndefined) match {
              case (true, true) | (_, false) => reader.from(cursor)
              case (false, true) => cursor.failed(keyNotFound(constValue[N]))
            }

          h -> readTuple[t, N + 1](cursors, keyNotFound) match {
            case (Right(h), Right(t)) => Right(Utils.widen[h *: t, T](h *: t))
            case (Left(h), Left(t)) => Left(h ++ t)
            case (_, Left(failures)) => Left(failures)
            case (Left(failures), _) => Left(failures)
          }

        case _: EmptyTuple =>
          Right(Utils.widen[EmptyTuple, T](EmptyTuple))
      }

    inline def summonConfigReader[A]: ConfigReader[A] =
      summonFrom {
        case reader: ConfigReader[A] => reader
        case given Mirror.Of[A] => derived[A]
      }

    /** Override pureconfig's default `type` field type discriminator for sealed traits.
      * Instead, use `circe`-like format with a single-key object. Example:
      *
      * {{{
      *   sealed trait AorB
      *   final case class A(a: Int) extends AorB
      *   final case class B(b: String) extends AorB
      *
      *   final case class Config(values: List[AorB])
      * }}}
      *
      * in config:
      *
      * {{{
      *   config {
      *     values = [{ A { a = 5 } }, { B { b = cba } }]
      *   }
      * }}}
      */
    inline def derivedSum[A](using m: Mirror.SumOf[A]): ConfigReader[A] = {
      new ConfigReader[A] {
        def from(cur: ConfigCursor): ConfigReader.Result[A] = {
          val options: Map[String, ConfigReader[A]] =
            Utils
              .transformedLabels[A](fieldMapping)
              .zip(deriveForSubtypes[m.MirroredElemTypes, A])
              .toMap

          for {
            objCur <- cur.asObjectCursor
            _ <-
              if (objCur.keys.size == 1) {
                Right(())
              } else {
                val msg =
                  s"""Invalid format for sealed trait, found multiple or no keys in object. Expected a single-key object like `{ "$$K": {} }`
                     |where `$$K` can be one of ${options.map(_._1).mkString}""".stripMargin
                Left(
                  ConfigReaderFailures(
                    objCur.failureFor(
                      NoValidCoproductOptionFound(objCur.objValue, Seq("_invalid_format_" -> ConfigReaderFailures(ThrowableFailure(new RuntimeException(msg), None))))
                    )
                  )
                )
              }
            res <- {
              val key = objCur.keys.head
              options.get(key) match {
                case Some(reader) =>
                  (objCur atKey key)
                    .flatMap(reader.from)
                case None =>
                  Left(ConfigReaderFailures(objCur.failureFor(InvalidCoproductOption(key))))
              }
            }
          } yield res
        }
      }
    }

    inline def deriveForSubtypes[T <: Tuple, A]: List[ConfigReader[A]] =
      inline erasedValue[T] match {
        case _: (h *: t) => deriveForSubtype[h, A] :: deriveForSubtypes[t, A]
        case _: EmptyTuple => Nil
      }

    inline def deriveForSubtype[A0, A]: ConfigReader[A] =
      summonFrom {
        case reader: ConfigReader[A0] =>
          reader.asInstanceOf[ConfigReader[A]]

        case given Mirror.Of[A0] =>
          derived[A0].asInstanceOf[ConfigReader[A]]
      }

    // not published in pureconfig Scala 3 version, copied from Scala 2 version
    /**
      * A failure reason given when a valid option for a coproduct cannot be found.
      *
      * @param value          the ConfigValue that was unable to be mapped to a coproduct option
      * @param optionFailures the failures produced when attempting to read coproduct options
      */
    final case class NoValidCoproductOptionFound(value: ConfigValue, optionFailures: Seq[(String, ConfigReaderFailures)]) extends FailureReason {
      def description = {
        val baseDescription = s"No valid coproduct option found for '${value.render(ConfigRenderOptions.concise())}'."
        baseDescription + (if (optionFailures.isEmpty) ""
                           else {
                             "\n" + optionFailures
                               .map {
                                 case (optionName, failures) =>
                                   s"Can't use coproduct option '$optionName':\n" + failures.prettyPrint(1)
                               }
                               .mkString("\n")
                           })
      }
    }

    // not published in pureconfig Scala 3 version, copied from Scala 2 version
    /**
      * A failure reason given when a provided coproduct option is invalid. This likely signals a bug in a CoproductHint
      * implementation, since the provided option isn't a valid one for the CoproductHint's type.
      *
      * @param option the coproduct option that is invalid
      */
    final case class InvalidCoproductOption(option: String) extends FailureReason {
      def description =
        s"""|The provided option '$option' is invalid for the CoproductHint's type. There's likely a bug in the
            |CoproductHint implementation.""".stripMargin
    }

    private[config] val fieldMapping: ConfigFieldMapping = ConfigFieldMapping(CamelCase, CamelCase)

  }

}
