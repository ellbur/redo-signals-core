
package redosignals

import java.awt.event.{ActionEvent, ActionListener}
import java.util.Date

import scala.concurrent.duration.Duration

trait Clocks { this: RedoSignals.type =>
  def clock(resolution: Duration)(implicit timerBackend: TimerBackend): Target[Date] = new ComputedTarget[Date] {
    import timerBackend.startTimer
    override protected def compute(): Date = {
      startTimer(resolution.toMillis.toInt, upset()())
      new Date
    }
  }
}
