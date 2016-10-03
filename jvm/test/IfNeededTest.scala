
import redosignals.{Target, Observer, Source}

object IfNeededTest extends App with Observer {
  {
    val boxes = new Source[Seq[(String, Target[Int])]](Seq())

    boxes.asNeededForeach { (boxes, n) =>
      println("Loop")
      boxes foreach { case (name, number) =>
        number.ifNeeded(n) { x =>
          println(s"$name $x")
        }
      }
    }

    val a = ("a", new Source[Int](0))
    boxes() = boxes.now :+ a

    val b = ("b", new Source[Int](0))
    boxes() = boxes.now :+ b

    a._2() = 1
    a._2() = 2
    a._2() = 3

    b._2() = 1
    b._2() = 2
    b._2() = 3

    val c = ("c", new Source[Int](0))
    boxes() = boxes.now :+ c

    c._2() = 1
    c._2() = 2
    c._2() = 3
  }
}
