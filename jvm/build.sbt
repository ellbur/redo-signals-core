
organization := "com.github.ellbur"

name := "redo-signals-core"

version := "0.9-SNAPSHOT"

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  "cc.co.scala-reactive" %% "reactive-core" % "0.3.0"
)

resolvers += "Local Maven Repository" at file(Path.userHome.absolutePath + "/.m2/repository").toURL.toString

scalaSource in Compile <<= baseDirectory(_ / "src")

scalaSource in Test <<= baseDirectory(_ / "test")

resourceDirectory in Compile <<= baseDirectory(_ / "resources")

resourceDirectory in Test <<= baseDirectory(_ / "test-resources")

resolvers += "jitpack" at "https://jitpack.io"

credentials += Credentials(Path.userHome / ".ivy2" / ".local-archive-internal-credentials.txt")

publishMavenStyle := true

publishTo := Some("local internal" at "http://localhost:8080/repository/internal/")

resolvers += "local internal" at "http://localhost:8080/repository/internal/"

resolvers += "parent internal" at "http://192.168.30.1:8080/repository/internal/"

resolvers += "Local Maven Repository" at file(Path.userHome.absolutePath + "/.m2/repository").toURL.toString

