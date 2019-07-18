package com.github.pshirshov.izumi.distage.testkit.services.st.dtest

import org.scalatest.words.{CanVerb, MustVerb, ShouldVerb}

@org.scalatest.Finders(value = Array("org.scalatest.finders.WordSpecFinder"))
trait ScalatestWords extends ShouldVerb with MustVerb with CanVerb {

}
