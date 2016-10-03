
package redosignals

import java.awt.event.{ActionEvent, ActionListener}
import scala.concurrent.duration.Duration
import math._

trait Timing { self: RedoSignals.type =>
  def mapWithTimer[A, B](from: Target[A])(f: A => (B, Option[Duration]))(implicit timerBackend: TimerBackend): Target[B] = new ComputedTarget[B] {
    import timerBackend.startTimer

    private var timer: Option[Timer] = None

    override protected def compute(): B = {
      timer foreach (_.stop())
      timer = None

      val next = from.rely(currentObserving, upset)
      val (b, reschedule) = f(next)

      reschedule foreach { duration =>
        startTimer(duration.toMillis.toInt, new ActionListener {
          override def actionPerformed(e: ActionEvent): Unit = {
            upset()()
          }
        })
      }

      b
    }
  }

  sealed trait Similarity
  case object Dissimilar extends Similarity
  case class SimilarFor(duration: Duration) extends Similarity

  def coalesceSimilar[A](from: Target[A])(decide: (A, A) => Similarity)(implicit timerBackend: TimerBackend): Target[A] = new ComputedTarget[A] { self =>
    import timerBackend.startTimer

    private var timer: Option[Timer] = None
    private var last: Option[(A, Long)] = None

    override protected def compute(): A = {
      timer foreach (_.stop())
      timer = None

      val next = from.rely(currentObserving, upset)
      last match {
        case None =>
          last = Some((next, System.currentTimeMillis))
          next
        case Some((last, lastTime)) =>
          decide(last, next) match {
            case Dissimilar =>
              self.last = Some((next, System.currentTimeMillis))
              next
            case SimilarFor(duration) =>
              val lateness = (System.currentTimeMillis - lastTime).toInt
              val okTime = duration.toMillis.toInt - lateness
              if (okTime > 0) {
                val newTimer =
                  startTimer(okTime, new ActionListener {
                    override def actionPerformed(e: ActionEvent): Unit = {
                      upset()()
                    }
                  })
                timer = Some(newTimer)
                last
              }
              else {
                self.last = Some((next, System.currentTimeMillis))
                next
              }
          }
      }
    }
  }

  def extendFor(from: Target[Boolean], duration: Duration)(implicit timerBackend: TimerBackend): Target[Boolean] = new ComputedTarget[Boolean] {
    import timerBackend.startTimer

    private var timer: Option[Timer] = None
    private var wentFalseTime: Option[Long] = None

    override protected def compute(): Boolean = {
      timer foreach (_.stop())
      timer = None

      val next = from.rely(currentObserving, upset)

      next match {
        case true =>
          wentFalseTime = None
          true

        case false =>
          val remainingMillis =
            wentFalseTime match {
              case None =>
                wentFalseTime = Some(System.currentTimeMillis)
                Some(duration.toMillis)

              case Some(wentFalseTime) =>
                val remaining = duration.toMillis - (System.currentTimeMillis - wentFalseTime)
                if (remaining > 0)
                  Some(remaining)
                else
                  None
            }

          remainingMillis match {
            case None =>
              false

            case Some(remainingMillis) =>
              val newTimer =
                startTimer(remainingMillis.toInt, new ActionListener {
                  override def actionPerformed(e: ActionEvent): Unit = {
                    upset()()
                  }
                })
              timer = Some(newTimer)

              true
          }
      }
    }
  }

  def rampDown(from: Target[Double], fallRatePerS: Double, samplingPeriod: Duration)(implicit timerBackend: TimerBackend): Target[Double] = new ComputedTarget[Double] {
    import timerBackend.startTimer
    private var timer: Option[Timer] = None

    private var peg: Option[(Long, Double, Double)] = None

    override protected def compute(): Double = {
      timer foreach (_.stop())
      timer = None

      val nextValue = from.rely(currentObserving, upset)
      val nextTime = System.currentTimeMillis()

      peg match {
        case None =>
          peg = Some((nextTime, nextValue, nextValue))
          nextValue

        case Some((lastTime, lastDesiredValue, lastActualValue)) =>
          val timeDelta = (nextTime - lastTime) / 1e3

          val minimumAllowedValue = max(lastDesiredValue, lastActualValue - fallRatePerS*timeDelta)

          if (nextValue < minimumAllowedValue) {
            val compromiseValue = minimumAllowedValue

            val newTimer =
              startTimer(samplingPeriod.toMillis.toInt, new ActionListener {
                override def actionPerformed(e: ActionEvent): Unit = {
                  upset()()
                }
              })
            timer = Some(newTimer)

            peg = Some((nextTime, nextValue, compromiseValue))

            compromiseValue
          }
          else {
            nextValue
          }
      }
    }
  }
}
