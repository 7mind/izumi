package izumi.functional.bio.test

import cats.effect.laws.util.{TestContext, TestInstances}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.prop.Configuration
import org.typelevel.discipline.scalatest.FunSuiteDiscipline

trait CatsLawsTestBase extends AnyFunSuite with FunSuiteDiscipline with TestInstances with Configuration
