
import redosignals.{Observer, Source}

object BlahTest extends App with Observer {
  {
    case class Payload(name: String)
    trait Worker
    class Type1Worker(val payload: Payload) extends Worker
    class Type2Worker(val payload: Payload) extends Worker
    
    val rootSourceList = new Source[Seq[Payload]](Seq())
    
    val sourceList1 = rootSourceList map (_ filter (_.name.startsWith("a")))
    val sourceList2 = rootSourceList map (_ filter (_.name.startsWith("b")))
    
    val workerList = new Source[Seq[Worker]](Seq(), debugName = Some("workerList"))
    
    sourceList1.zipWithStalenessFrom(Seq()).foreachDelayed { xs => implicit u =>
      workerList.updateLater {
        xs map { case (oldXs, newXs) =>
          workerList.now ++ ((newXs.toSet -- oldXs.toSet) map (new Type1Worker(_)))
        }
      }
    }(workerList.redoObserving)
    
    sourceList2.zipWithStalenessFrom(Seq()).foreachDelayed { xs => implicit u =>
      workerList.updateLater {
        xs map { case (oldXs, newXs) =>
          workerList.now ++ ((newXs.toSet -- oldXs.toSet) map (new Type2Worker(_)))
        }
      }
    }(workerList.redoObserving)
    
    workerList foreach { workers =>
      println(s"Now have ${workers.length} workers")
    }
    
    // One update
    rootSourceList() = Seq(Payload("a 1"), Payload("b 1"))
  }
}
