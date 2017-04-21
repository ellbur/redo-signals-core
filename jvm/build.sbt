
organization := "com.github.ellbur"

name := "redo-signals-core"

version := "0.9.6"

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  "com.github.ellbur" %% "reactive-core-jvm" % "0.3.1"
)

resolvers += "Local Maven Repository" at file(Path.userHome.absolutePath + "/.m2/repository").toURL.toString

scalaSource in Compile <<= baseDirectory(_ / "src")

scalaSource in Test <<= baseDirectory(_ / "test")

resourceDirectory in Compile <<= baseDirectory(_ / "resources")

resourceDirectory in Test <<= baseDirectory(_ / "test-resources")

resolvers ++= Seq(
  "jitpack" at "https://jitpack.io"
)
