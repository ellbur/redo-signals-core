
package redosignals

import scala.ref.WeakReference

trait Utils { self: RedoSignals.type =>
  implicit class SetLike[A](f: A => Unit) {
    def like(as: Target[A])(implicit obs: ObservingLike): Unit =
      as foreach f
  }
  
  def loopOn[A](sig: Target[A])(f: A => Unit)(implicit obs: ObservingLike) {
    obs.observe(f)
    obs.observe(sig)
    loopOnWeak(sig)(WeakReference(f))
  }

  def loopOnWeak[A](sig: Target[A])(f: WeakReference[A => Unit])(implicit obs: ObservingLike) {
    var current: Option[A] = None
    val observing = Var[Observing](new Observing)
    obs.observe(observing)
    def go() {
      f.get foreach { f =>
        val next = sig.rely(observing.it, changed)
        if (!current.contains(next)) {
          current = Some(next)
          f(next)
        }
      }
    }
    lazy val changed = () => () => {
      observing.it = new Observing
      go()
    }
    go()
  }

  def loopOnSoLongAs[A](sig: Target[A])(check: => Boolean)(f: A => Unit): Unit = {
    var current: Option[A] = None
    var observing = new Observing
    def go() {
      if (check) {
        val next = sig.rely(observing, changed)
        if (!current.contains(next)) {
          current = Some(next)
          f(next)
        }
      }
    }
    lazy val changed = () => () => {
      observing = new Observing
      go()
    }
    go()
  }

  def loopOnDebug[A](sig: Target[A])(name: String)(f: A => Unit)(implicit obs: ObservingLike) {
    obs.observe(f)
    obs.observe(sig)
    loopOnWeakDebug(sig)(name)(WeakReference(f))
  }

  private var numberCounter: Int = 0

  private case class Var[T](var it: T)

  def loopOnWeakDebug[A](sig: Target[A])(name: String)(f: WeakReference[A => Unit])(implicit obs: ObservingLike) {
    val number = numberCounter
    numberCounter += 1
    println(s"Starting loop $name $number")
    var current: Option[A] = None
    val observing = Var[Observing](new Observing)
    obs.observe(observing)
    def go() {
      println(s"$name $number go()")
      f.get match {
        case Some(f) =>
          println(s"$name $number present")
          val next = sig.rely(observing.it, changed)
          println(s"$name $number got next $next vs current $current")
          if (!current.contains(next)) {
            println(s"Decided to update it")
            current = Some(next)
            f(next)
          }
        case None =>
          println(s"$name $number absent")
      }
    }
    lazy val changed = () => () => {
      observing.it = new Observing
      println(s"$name $number changed()")
      go()
    }
    go()
  }

  def delayingUpdates(f: UpdateSink => Unit) {
    val sink = new UpdateSink
    f(sink)
    sink()
  }

  implicit class LastValid[A](t: Target[Option[A]]) {
    def lastValid[B>:A](init: B): Target[B] = {
      var now: B = init
      t map {
        case Some(x) =>
          now = x
          x
        case None =>
          now
      }
    }
  }

  import TargetMutability.Tracker

  def tracking[A](f: Tracker => A): Target[A] = TargetMutability.tracking(f)

  def trackingRepeat(f: Tracker => Unit)(implicit obs: ObservingLike) = {
    tracking { t =>
      f(t)
      new AnyRef
    } foreach { _ => }
  }

  def trackingFor(update: => Unit)(f: Tracker => Unit)(implicit obs: ObservingLike) = TargetMutability.trackingFor(update)(f)(obs)

  def immediatelyCheckingChanged[A](sig: Target[A]): Target[A] = sig.immediatelyCheckingChanged

  implicit class TargetConstant[A](a: A) {
    def constant: Target[A] = TargetMutability.constant(a)
  }
}
