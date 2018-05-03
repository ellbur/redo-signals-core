
organization := "com.github.ellbur"

name := "redo-signals-core"

version := "0.10.1"

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  "com.github.ellbur" %% "reactive-core-jvm" % "0.3.1"
)

resolvers += "Local Maven Repository" at file(Path.userHome.absolutePath + "/.m2/repository").toURL.toString

scalaSource in Compile := { baseDirectory.value / "src" }

scalaSource in Test := { baseDirectory.value / "test" }

resourceDirectory in Compile := { baseDirectory.value / "resources" }

resourceDirectory in Test := { baseDirectory.value / "test-resources" }

resolvers ++= Seq(
  "jitpack" at "https://jitpack.io"
)

//publishTo := Some("ellbur repo" at "s3://ellbur-public-maven-repository/")
publishTo := Some("Local Maven Repository" at file(Path.userHome.absolutePath + "/.m2/repository").toURL.toString)

