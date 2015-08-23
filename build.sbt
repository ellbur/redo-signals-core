
lazy val root = project.in(file(".")).aggregate(jsProject, jvmProject).settings(
  scalaVersion := "2.11.6"
)

lazy val dualProject = crossProject.in(file(".")).settings(
  organization := "com.github.ellbur",
  name := "redo-signals-core",
  version := "0.8-SNAPSHOT",
  scalaVersion := "2.11.6",
  libraryDependencies ++= Seq(
    "cc.co.scala-reactive" %%% "reactive-core" % "0.3.0"
  ),
  resolvers += "Local Maven Repository" at file(Path.userHome.absolutePath + "/.m2/repository").toURL.toString
)

lazy val jsProject = dualProject.js.settings(
  publishTo := Some(Resolver.file("file", new File(Path.userHome.absolutePath + "/.m2/repository"))),
  scalaSource in Compile <<= baseDirectory(_ / "../src"),
  javaSource in Compile <<= baseDirectory(_ / "../src"),
  scalaSource in Test <<= baseDirectory(_ / "../test"),
  javaSource in Test <<= baseDirectory(_ / "../test"),
  resourceDirectory in Compile <<= baseDirectory(_ / "../resources"),
  resourceDirectory in Test <<= baseDirectory(_ / "../test-resources")
)

lazy val jvmProject = dualProject.jvm.settings(
  publishTo := Some(Resolver.file("file", new File(Path.userHome.absolutePath + "/.m2/repository"))),
  scalaSource in Compile <<= baseDirectory(_ / "../src"),
  javaSource in Compile <<= baseDirectory(_ / "../src"),
  scalaSource in Test <<= baseDirectory(_ / "../test"),
  javaSource in Test <<= baseDirectory(_ / "../test"),
  resourceDirectory in Compile <<= baseDirectory(_ / "../resources"),
  resourceDirectory in Test <<= baseDirectory(_ / "../test-resources")
)

