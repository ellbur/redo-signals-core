
package redosignals

sealed trait MaybeFinished[+A] { val it: A }
case class NotFinished[+A](it: A) extends MaybeFinished[A]
case class Finished[+A](it: A) extends MaybeFinished[A]
