
organization := "com.github.ellbur"

name := "redo-signals-core"

version := "0.9-SNAPSHOT"

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  "cc.co.scala-reactive" %%% "reactive-core" % "0.3.0"
)

resolvers += "Local Maven Repository" at file(Path.userHome.absolutePath + "/.m2/repository").toURL.toString

scalaSource in Compile <<= baseDirectory(_ / "src")

scalaSource in Test <<= baseDirectory(_ / "test")

resourceDirectory in Compile <<= baseDirectory(_ / "resources")

resourceDirectory in Test <<= baseDirectory(_ / "test-resources")

enablePlugins(ScalaJSPlugin)

