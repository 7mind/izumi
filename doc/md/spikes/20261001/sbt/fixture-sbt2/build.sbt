scalaVersion := "2.13.18"
libraryDependencies += "org.scala-sbt" % "test-interface" % "1.0"
Test / testFrameworks := Seq(new TestFramework("spike.DiscoveryFramework"), new TestFramework("spike.OtherFramework"))
Test / parallelExecution := false
enablePlugins(WholeSetPlugin)
