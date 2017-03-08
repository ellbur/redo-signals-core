
// http://www.scala-sbt.org/0.13/docs/Multi-Project.html
lazy val root = (project in file(".")) aggregate (js) settings (publish := {})

lazy val js = project

