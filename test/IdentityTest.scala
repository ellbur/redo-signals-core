
import redosignals.{Observer, Source}

object IdentityTest extends App with Observer {
  {
    val x = new Source[Int](0)
    val i = x.identity
    val y0 = i ~ 0
    val y1 = i ~ 1
    val y2 = i ~ 2
    val z0 = y0 map { y =>
      println("Recomputing 0")
      y
    }
    val z1 = y1 map { y =>
      println("Recomputing 1")
      y
    }
    val z2 = y2 map { y =>
      println("Recomputing 2")
      y
    }
    z0 foreach { z => println("z0 = " + z) }
    z1 foreach { z => println("z1 = " + z) }
    z2 foreach { z => println("z2 = " + z) }

    println()
    x() = 1

    println()
    x() = 2

    println()
    x() = 0
  }
}
