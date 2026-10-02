package izumi.functional.bio.laws

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.prop.Configuration
import org.typelevel.discipline.scalatest.FunSuiteDiscipline

trait CatsLawsTestBase extends AnyFunSuite with FunSuiteDiscipline with Configuration
