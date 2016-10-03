
package redosignals

import scala.concurrent.duration.Duration

trait Clocks { this: RedoSignals.type =>
  def clock(resolution: Duration)(implicit timerBackend: TimerBackend): Target[Long] = new ComputedTarget[Long] {
    import timerBackend.startTimer
    override protected def compute(): Long = {
      startTimer(resolution.toMillis.toInt, upset()())
      System.currentTimeMillis()
    }
  }
}
