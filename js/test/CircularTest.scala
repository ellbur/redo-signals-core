
import redosignals.{Observing, Source}

object CircularTest extends App {
  val globalObs = new Observing
  
  {
    val a = new Source[Seq[Int]](Seq())
    val b = new Source[Seq[Int]](Seq())
    
    a.zipWithStalenessFrom(Seq()).foreachDelayed { case (aBefore, aAfter) => implicit u =>
      val addSet = aAfter.toSet -- aBefore.toSet
      val delSet = aBefore.toSet -- aAfter.toSet
      b <<= (b.now filter (b => !delSet.contains(b))) ++ addSet.toSeq
    }(b.redoObserving)
    
    b.foreachDelayed { b => implicit u =>
      val bSet = b.toSet
      a <<= a.now filter bSet.contains
    } (a.redoObserving)
    
    a.foreach(a => println(s"a = $a"))(globalObs)
    b.foreach(b => println(s"b = $b"))(globalObs)
    
    a() = a.now :+ 1
    a() = a.now :+ 2
    b() = b.now filter (_ != 1)
    a() = a.now filter (_ != 2)
  }
}
