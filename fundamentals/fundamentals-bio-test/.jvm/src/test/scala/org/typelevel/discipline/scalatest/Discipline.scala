// inline discipline-scalatest because it's compiled vs 3.2 version of scalatest, and we use scalatest 3.3

/*
 * Copyright (c) 2019 Typelevel
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package org.typelevel.discipline
package scalatest

import org.scalactic.Prettifier
import org.scalactic.source.Position
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.funspec.AnyFunSpecLike
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.prop.Configuration
import org.scalatest.prop.Configuration.PropertyCheckConfiguration
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.scalacheck.Checkers

trait Discipline { self: Configuration =>

  /**
    * Convert from our configuration type to the one required by `Checkers`.
    *
    * We don't extend `Checkers` because we want to leave the user as much
    * control as possible over available testing styles. Unfortunately we need
    * this conversion because ScalaTest defines `PropertyCheckConfiguration`
    * as a case class in the `Configuration` trait.
    */
  final protected[this] def convertConfiguration(
    config: PropertyCheckConfiguration
  ): PropertyCheckConfiguration = config

  def checkAll(
    name: String,
    ruleSet: Laws#RuleSet,
  )(implicit
    config: PropertyCheckConfiguration,
    prettifier: Prettifier,
    pos: Position,
  ): Unit
}

trait FlatSpecDiscipline extends Discipline { self: AnyFlatSpecLike & Configuration =>
  final def checkAll(name: String, ruleSet: Laws#RuleSet)(implicit config: PropertyCheckConfiguration, prettifier: Prettifier, pos: Position): Unit =
    ruleSet.all.properties.toList match {
      case first :: rest =>
        name should first._1 in Checkers.check(first._2)(convertConfiguration(config), prettifier, pos)

        for ((id, prop) <- rest)
          it should id in Checkers.check(prop)(convertConfiguration(config), prettifier, pos)

      case Nil =>
    }
}

trait FunSpecDiscipline extends Discipline { self: AnyFunSpecLike & Configuration =>
  final def checkAll(name: String, ruleSet: Laws#RuleSet)(implicit config: PropertyCheckConfiguration, prettifier: Prettifier, pos: Position): Unit =
    describe(name) {
      for ((id, prop) <- ruleSet.all.properties)
        it(id) {
          Checkers.check(prop)(convertConfiguration(config), prettifier, pos)
        }
    }
}

trait FunSuiteDiscipline extends Discipline { self: AnyFunSuiteLike & Configuration =>
  final def checkAll(name: String, ruleSet: Laws#RuleSet)(implicit config: PropertyCheckConfiguration, prettifier: Prettifier, pos: Position): Unit =
    for ((id, prop) <- ruleSet.all.properties)
      test(s"$name.$id") {
        Checkers.check(prop)(convertConfiguration(config), prettifier, pos)
      }
}

trait WordSpecDiscipline extends Discipline { self: AnyWordSpec & Configuration =>

  def checkAll(name: String, ruleSet: Laws#RuleSet)(implicit config: PropertyCheckConfiguration, prettifier: Prettifier, pos: Position): Unit =
    for ((id, prop) <- ruleSet.all.properties)
      registerTest(s"$name.$id") {
        Checkers.check(prop)(convertConfiguration(config), prettifier, pos)
      }
}
