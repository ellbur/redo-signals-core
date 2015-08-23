
package redosignals

trait TimerBackend {
  def startTimer(delayMS: Int, action: => Unit): Timer
}

trait Timer {
  def stop()
}
