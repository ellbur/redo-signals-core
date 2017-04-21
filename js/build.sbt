
organization := "com.github.ellbur"

name := "redo-signals-core"

version := "0.9.4"

scalaVersion := "2.12.1"

libraryDependencies ++= Seq(
  "com.github.ellbur" % "reactive-core-js" % "0.3.1" // Note the syntax work-around for jitpack!
)

resolvers += "Local Maven Repository" at file(Path.userHome.absolutePath + "/.m2/repository").toURL.toString

scalaSource in Compile <<= baseDirectory(_ / "src")

scalaSource in Test <<= baseDirectory(_ / "test")

resourceDirectory in Compile <<= baseDirectory(_ / "resources")

resourceDirectory in Test <<= baseDirectory(_ / "test-resources")

enablePlugins(ScalaJSPlugin)

resolvers ++= Seq(
  "jitpack" at "https://jitpack.io"
)
