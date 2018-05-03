
import redosignals.Source

object ForeachDelayedLeakTest extends App {
  {
    val a = new Source[Int](0)
    
    var b: Source[Int] = new Source[Int](0) {
      override def finalize() = {
        super.finalize()
        println("Finalizing b")
      }
    }
    
    a.foreachDelayed { a => implicit u =>
      b.updateLater(a)
    } (b.redoObserving)
    
    a() = 1
    
    b = null
    
    System.gc()
    System.gc()
    System.gc()
    
    Thread.sleep(100)
  
    System.gc()
    System.gc()
    System.gc()
    
    println("Exiting")
  }
}

