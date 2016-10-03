
import redosignals.{Observer, Source}
import redosignals.RedoSignals._

object BasicTest extends App with Observer {
  {
    val s = new Source[Int](0)
    val x = tracking { implicit t => s.track + 1 }
    x foreach println

    s() = 1
    s() = 2
  }
}
