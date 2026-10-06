// Compile the production plugins from this checkout before the repository build loads.
Compile / unmanagedSourceDirectories ++= {
  val repository = baseDirectory.value.getParentFile
  Seq(
    "sbt-plugins/sbt-distage-testkit/src/main/scala",
    "sbt-plugins/sbt-distage-testkit/src/main/scala-3",
    "sbt-plugins/sbt-distage-testkit/src/main/java",
    "sbt-plugins/sbt-distage-testkit-js/src/main/scala",
    "sbt-plugins/sbt-distage-testkit-native/src/main/scala",
    "distage/distage-test-protocol/src/main/scala",
    "distage/distage-test-protocol/.jvm/src/main/scala",
    "distage/distage-test-protocol/.jvm/src/main/java",
  ).map(repository / _)
}

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core" % PV.circe,
  "io.circe" %% "circe-parser" % PV.circe,
  "net.bytebuddy" % "byte-buddy" % PV.bytebuddy,
)
