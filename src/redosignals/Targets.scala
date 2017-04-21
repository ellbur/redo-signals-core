
package redosignals

import reactive.{Subscription, EventSource, EventStream}
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.concurrent._
import scala.concurrent.duration.Duration
import scala.ref.WeakReference
import scala.util.Try

trait Target[+A] extends TargetMutability.TargetLike[A] {
  thisTarget =>
  def track(implicit tracker: TargetMutability.Tracker): A = tracker.track(this)

  def trackDebug(name: String)(implicit tracker: TargetMutability.Tracker): A = tracker.track(this, name)

  def rely(obs: ObservingLike, changed: () => () => Unit): A

  def zip[B](other: Target[B]): Target[(A, B)] =
    new Pairing[A, B](this, other)

  def unzip[X, Y](implicit pf: A <:< (X, Y)): (Target[X], Target[Y]) =
    (
      this map (a => pf(a)._1),
      this map (a => pf(a)._2)
    )

  def map[B](f: A => B): Target[B] =
    new Mapping[A, B](this, f)

  def mapWithTimer[B](f: A => (B, Option[Duration]))(implicit timerBackend: TimerBackend) = RedoSignals.mapWithTimer[A, B](this)(f)

  def flatMap[B](f: A => Target[B]): Target[B] =
    new Switch(this map f)

  def split[X, Y](implicit isAnEither: A <:< Either[X, Y]) = new {
    def left[C1](left: Target[X] => C1) = new {
      def right[C2 >: C1](right: Target[Y] => C2): Target[C2] =
        new EitherSplit(thisTarget map isAnEither, left, right)
    }
  }

  def regroup[B,C](f: A => B)(g: (B, Target[A]) => C): Target[C] =
    new FunctionSplit(this, f, g)

  def regroupSeq[B,C](f: A => Seq[B])(g: (B, Target[A]) => C): Target[Seq[C]] =
    new SeqFunctionSplit(this, f, g)

  def biFlatMap[B](f: A => BiTarget[B]): BiTarget[B] =
    new BiSwitch(this map f)

  def now: A

  def foreach(f: A => Unit)(implicit obs: ObservingLike) {
    RedoSignals.loopOn(this)(f)
  }
  
  def foreachDelayed(f: A => UpdateSink => Unit)(implicit obs: ObservingLike) {
    RedoSignals.loopOnDelayed(this)(f)
  }

  def forSoLongAs(check: => Boolean)(f: A => Unit)(implicit obs: ObservingLike): Unit = {
    RedoSignals.loopOnSoLongAs(this)(check)(f)
  }

  def foreachDebug(name: String)(f: A => Unit)(implicit obs: ObservingLike): Unit = {
    println(s"foreachDebug($name) { ... }")
    RedoSignals.loopOnDebug(this)(name)(f)
  }

  def apply(f: A => Unit)(implicit obs: ObservingLike) {
    foreach(f)(obs)
  }

  def immediatelyCheckingChanged: Target[A] = new ImmediatelyCheckingChanged[A](this)

  def zipWithStaleness: Target[(A, A)] = {
    var staleness: A = now
    RedoSignals.tracking { implicit t =>
      val last = staleness
      staleness = track
      (last, staleness)
    }
  }

  def zipWithStalenessFrom[B>:A](default: B): Target[(B, B)] = {
    var staleness: B = default
    RedoSignals.tracking { implicit t =>
      val last = staleness
      staleness = track
      (last, staleness)
    }
  }

  def collect[B](f: PartialFunction[A, B]): Target[Option[B]] = {
    var current: Option[B] = None
    RedoSignals.tracking { implicit t =>
      current = f.lift(track) orElse current
      current
    }
  }

  def window[B](ev: EventStream[B])(implicit pf: A <:< Boolean): EventStream[B] =
    new EventSource[B] with reactive.Observing { resultingStream =>
      implicit val redoObserving = new Observing

      ev foreach { x =>
        if (thisTarget.now)
          resultingStream.fire(x)
      }
    }

  def rampDown(fallRatePerS: Double, samplingPeriod: Duration)(implicit pf: A <:< Double, timerBackend: TimerBackend): Target[Double] =
    RedoSignals.rampDown(this map (a => a: Double), fallRatePerS, samplingPeriod)

  def mapEvents[B](f: A => EventStream[B]): EventStream[B] = new EventSource[B] with redosignals.Observer { eventSource =>
    var currentSubscription: Option[Subscription] = None
    thisTarget.foreach { a =>
      val childStream = f(a)
      currentSubscription foreach (_.unsubscribe())
      val subscription = childStream.subscribe { b =>
        eventSource.fire(b)
      }
      currentSubscription = Some(subscription)
    } (eventSource)
  }

  def mapOptionalEvents[B](f: A => Option[EventStream[B]]): EventStream[B] = this mapEvents (a => f(a) getOrElse EventStream.empty)

  def identity[B>:A] = new IdentityRoot[B](thisTarget)

  def throttleBy(accepted: Target[Int]): Target[(A, Int)] = new ThrottledByAcceptance[A](thisTarget, accepted)

  def changes: EventStream[A] = {
    val source = new EventSource[A] with Observer
    thisTarget.foreach { a => source.fire(a) } (source.redoObserving)
    source
  }

  def asNeededForeach(f: (A, ForeachNesting) => Unit)(implicit obs: ObservingLike): Unit = AsNeededForeach.run(thisTarget)(f)(obs)

  def ifNeeded(n: ForeachNesting)(f: A => Unit) = n.ifNeeded(thisTarget)(f)

  def nextSome[B](implicit isAnOption: A <:< Option[B]): Future[B] = {
    val promise = Promise[B]()
    val promiseFuture = promise.future
    val observing = new Observing
    // This is just to prevent garbage collection :/
    promiseFuture.andThen { case t =>
      System.out.println(observing)
    } (concurrent.ExecutionContext.Implicits.global)
    thisTarget.foreach { a =>
      (a: Option[B]) match {
        case Some(b) => promise.success(b)
        case _ =>
      }
    } (observing)
    promiseFuture
  }
}

object Target {
  def latest[A](init: A, changes: EventStream[A]): Target[A] = {
    val source = new Source[A](init) with reactive.Observing
    changes.foreach(source.update)(source)
    source
  }
}

trait ActingTracker extends TargetMutability.Tracker {
  private var currentObserving = new Observing

  def track[A](t: Target[A]): A = {
    t.rely(currentObserving, { () =>
      invalidate()
      () =>
        update()
    })
  }

  def track[A](t: Target[A], name: String) = track(t)

  var valid: Boolean = false

  def run()

  def update() {
    if (!valid) {
      run()
      valid = true
    }
  }

  def invalidate() {
    valid = false
    currentObserving = new Observing
  }
}

class TargetTracker[A](f: TargetMutability.Tracker => A) extends ComputedTarget[A] { self =>
  protected def compute(): A = {
    f(new TargetMutability.Tracker {
      private val inputCache = new java.util.WeakHashMap[Target[_], Any]()

      def track[B](t: Target[B]): B =
        Option(inputCache.get(t)) match {
          case Some(x) => x.asInstanceOf[B]
          case None =>
            val x = t.rely(currentObserving, { () => inputCache.remove(t) ; self.upset() })
            inputCache.put(t, x)
            x
        }

      def track[B](t: Target[B], name: String): B =
        t.rely(currentObserving, {
          () =>
            println(s"$name triggered")
            self.upset()
        })
    })
  }
}

class DebugTargetTracker[A](f: TargetMutability.Tracker => A, name: String) extends DebugComputedTarget[A](name) { self =>
  protected def compute(): A = {
    println(s"$name compute()")
    f(new TargetMutability.Tracker {
      def track[B](t: Target[B]): B = t.rely(currentObserving, { () =>
        println(s"$name triggered")
        self.upset()
      })

      def track[B](t: Target[B], name: String): B =
        t.rely(currentObserving, {
          () =>
            println(s"${self.name} $name triggered")
            self.upset()
        })
    })
  }
}

class UpdateSink {
  val deferred = ArrayBuffer[() => Unit]()

  def defer(g: () => Unit) {
    deferred += g
  }

  def apply() {
    deferred foreach (_())
  }
}

trait ObservingLike {
  def observe(x: AnyRef)
}

class Observing extends ObservingLike {
  protected val observed = mutable.ArrayBuffer[AnyRef]()

  def observe(x: AnyRef) {
    observed += x
  }

  implicit val redoObserving = this
}

trait CancellableObservingLike {
  def observe(x: AnyRef): () => Unit
  def unobserve(x: AnyRef): Unit
}

class CancellableObserving extends CancellableObservingLike {
  protected val observed = mutable.Set[AnyRef]()

  def observe(x: AnyRef): () => Unit = {
    observed += x
    () => observed -= x
  }

  def unobserve(x: AnyRef): Unit = {
    observed -= x
  }
}

trait Observer extends ObservingLike {
  implicit val redoObserving = new Observing
  override def observe(x: AnyRef) = redoObserving.observe(x)
}

class DebugObserving(name: String) extends Observing {
  override def observe(x: AnyRef) {
    println(s"$name observing $x")
    observed += x
  }

  override def finalize(): Unit = {
    println(s"$name finalizing")
    super.finalize()
  }
}

trait CoTarget[-A] extends reactive.Observing { self =>
  lazy val redoObserving = new Observing

  def update(next: A): Unit = {
    delayedUpdate(next)()
  }

  def <<=(next: A)(implicit u: UpdateSink): Unit = {
    u.defer(delayedUpdate(next))
  }

  def <-!-(stream: reactive.EventStream[A]): Unit = {
    stream foreach update
  }

  def delayedUpdate(next: A): () => Unit

  def comap[B](f: B => A): CoTarget[B] = new CoTarget[B] {
    override def delayedUpdate(next: B): () => Unit =
      self.delayedUpdate(f(next))
  }
}

trait BiTarget[A] extends Target[A] with CoTarget[A] { self =>
  def containsBiMap[B](b: B)(implicit ok1: A =:= Set[B], ok2: Set[B] =:= A): BiTarget[Boolean] =
    new ComputedTarget[Boolean] with BiTarget[Boolean] {
      override protected def compute() = self.rely(currentObserving, upset) contains b

      override def delayedUpdate(next: Boolean): () => Unit =
        if (next)
          self.delayedUpdate(self.now + b)
        else
          self.delayedUpdate(self.now - b)
    }

  def ++(implicit num: Numeric[A]) = {
    val r = now
    update(num.plus(r, num.one))
    r
  }

  def joinTo(other: BiTarget[A]): Unit = {
    other() = self.now
    self.zipWithStaleness.foreach {
      case (a, b) => if (a != b) other() = b
    } (other.redoObserving)
    other.zipWithStaleness.foreach {
      case (a, b) => if (a != b) self() = b
    } (self.redoObserving)
  }

  def dualMap[B](f: A => B)(g: B => A): BiTarget[B] = new BiTarget[B] {
    override def now: B = f(self.now)
    override def rely(obs: ObservingLike, changed: () => () => Unit): B = f(self.rely(obs, changed))
    override def delayedUpdate(next: B): () => Unit = self.delayedUpdate(g(next))
  }

  def dualFlatMap[B](f: A => Target[B])(g: B => A): BiTarget[B] = {
    val flat = self flatMap f

    new BiTarget[B] {
      override def delayedUpdate(next: B): () => Unit = self.delayedUpdate(g(next))
      override def now: B = flat.now
      override def rely(obs: ObservingLike, changed: () => () => Unit): B = flat.rely(obs, changed)
    }
  }
}

object BiTarget {
  def apply[A](producer: Target[A], consumer: A => Unit) = new BiTarget[A] {
    override def delayedUpdate(next: A): () => Unit = () => consumer(next)
    override def now: A = producer.now
    override def rely(obs: ObservingLike, changed: () => () => Unit): A = producer.rely(obs, changed)
  }
}

class Source[A](init: A, debugName: Option[String] = None) extends BiTarget[A] {
  private var current: A = init
  private var listeners: Seq[WeakReference[() => () => Unit]] = Nil

  def delayedUpdate(next: A): () => Unit = {
    if (!(next == current))
      changed.fire(next)
    val toUpdate = synchronized {
      current = next
      val t = listeners
      listeners = Nil
      t
    }

    debugName foreach (n => println(s"$n Identified ${toUpdate.length} toUpdate"))

    val followUps = toUpdate flatMap (_.get map (_()))

    debugName foreach (n => println(s"$n ${followUps.length} are still actionable"))

    () => {
      debugName foreach (n => println(s"$n Performing $followUps followups"))
      followUps foreach (_())
    }
  }

  val changed = new reactive.EventSource[A]

  def rely(obs: ObservingLike, changed: () => (() => Unit)) = {
    debugName foreach (n => println(s"$n Source.rely"))
    obs.observe(changed)
    debugName foreach (n => println(s"$n before have ${listeners.length} listeners"))
    listeners = listeners :+ WeakReference(changed)
    listeners = listeners flatMap (l => l.get) map WeakReference.apply
    debugName foreach (n => println(s"$n now have ${listeners.length} listeners"))
    current
  }

  override def now: A = current
}

trait ComputedTarget[A] extends Target[A] with UnsafeUpsettable {
  protected var current: Option[A] = None
  private var listeners: Seq[WeakReference[() => () => Unit]] = Nil
  protected var currentObserving = new Observing

  protected def compute(): A

  def rely(obs: ObservingLike, changed: () => () => Unit): A = {
    obs.observe(changed)
    val it = synchronized {
      listeners = listeners :+ WeakReference(changed)
      listeners = listeners flatMap (l => l.get) map WeakReference.apply
      current
    }
    it match {
      case Some(x) => x
      case None =>
        synchronized {
          currentObserving = new Observing
          val computed = compute()
          current = Some(computed)
          computed
        }
    }
  }

  def now: A = {
    val it = synchronized {
      current
    }
    it match {
      case Some(x) => x
      case None =>
        val computed = compute()
        synchronized {
          current = Some(computed)
        }
        computed
    }
  }

  def upset: () => () => Unit = ComputedTarget.weakUpset(this)

  def unsafeUpset(): () => Unit = {
    val toNotify = synchronized {
      if (current.isDefined) {
        current = None
        val t = listeners
        listeners = Nil
        Some(t)
      }
      else None
    }
    toNotify match {
      case None => () => ()
      case Some(toUpdate) =>
        val followUps = toUpdate map (_.get map (_()) getOrElse (() => ()))

      { () =>
        followUps foreach (_())
      }
    }
  }
}

abstract class DebugComputedTarget[A](name: String) extends Target[A] with UnsafeUpsettable {
  protected var current: Option[A] = None
  private var listeners: Seq[WeakReference[() => () => Unit]] = Nil
  protected var currentObserving = new Observing

  protected def compute(): A

  def rely(obs: ObservingLike, changed: () => () => Unit): A = {
    println(s"$name rely()")
    obs.observe(changed)
    val it = synchronized {
      listeners = listeners :+ WeakReference(changed)
      listeners = listeners flatMap (l => l.get) map WeakReference.apply
      current
    }
    it match {
      case Some(x) =>
        println(s"$name current already current; leaving unchanged")
        x
      case None =>
        synchronized {
          currentObserving = new Observing
          val computed = compute()
          println(s"$name setting current to ${Some(computed)}")
          current = Some(computed)
          computed
        }
    }
  }

  def now: A = {
    val it = synchronized {
      current
    }
    it match {
      case Some(x) => x
      case None =>
        val computed = compute()
        synchronized {
          current = Some(computed)
        }
        computed
    }
  }

  def upset: () => () => Unit = ComputedTarget.weakUpset(this)

  def unsafeUpset(): () => Unit = {
    println(s"$name unsafeUpset()")
    val toNotify = synchronized {
      if (current.isDefined) {
        println(s"$name current.isDefined")
        current = None
        val t = listeners
        println(s"$name have ${t.length} listeners")
        listeners = Nil
        Some(t)
      }
      else {
        println(s"$name !!!current.isDefined")
        None
      }
    }
    toNotify match {
      case None => () => ()
      case Some(toUpdate) =>
        val followUps = toUpdate map (_.get map (_()) getOrElse (() => ()))

      { () =>
        println(s"$name performing ${followUps.length} followups")
        followUps foreach (_())
      }
    }
  }
}

trait UnsafeUpsettable {
  def unsafeUpset(): () => Unit
}

object ComputedTarget {
  def weakUpset(c: UnsafeUpsettable): () => () => Unit =
    weakWeakUpset(WeakReference(c))
  
  def weakWeakUpset(c: WeakReference[UnsafeUpsettable]): () => () => Unit = {
    () =>
      c.get map (_.unsafeUpset()) getOrElse (() => ())
  }
}

class Pairing[A, B](sigA: Target[A], sigB: Target[B]) extends ComputedTarget[(A, B)] {
  def compute() =
    (sigA.rely(currentObserving, upset), sigB.rely(currentObserving, upset))
}

class Mapping[A, B](sig: Target[A], f: A => B) extends ComputedTarget[B] {
  assert(sig != null)
  def compute() =
    f(sig.rely(currentObserving, upset))
}

class Pure[A](a: A) extends Target[A] {
  def rely(obs: ObservingLike, f: () => () => Unit) = a
  override def now: A = a
}

class Switch[A](sig: Target[Target[A]]) extends ComputedTarget[A] {
  def compute() =
    sig.rely(currentObserving, upset).rely(currentObserving, upset)
}

class BiSwitch[A](sig: Target[BiTarget[A]]) extends ComputedTarget[A] with BiTarget[A] {
  def compute() =
    sig.rely(currentObserving, upset).rely(currentObserving, upset)

  override def delayedUpdate(next: A): () => Unit =
    sig.now.delayedUpdate(next)
}

class ImmediatelyCheckingChanged[A](sig: Target[A]) extends Target[A] {
  protected var currentObserving = new Observing
  private var current: A = sig.rely(currentObserving, upset)
  private var listeners = mutable.ListBuffer[WeakReference[() => () => Unit]]()

  def rely(obs: ObservingLike, changed: () => () => Unit): A = {
    obs.observe(changed)
    listeners += WeakReference(changed)
    current
  }

  protected def upset(): () => Unit = {
    currentObserving = new Observing
    val toNotify = synchronized {
      val next = sig.rely(currentObserving, upset)
      if (next != current) {
        current = next
        val t = listeners.toSeq
        listeners.clear()
        Some(t)
      }
      else None
    }
    toNotify match {
      case None => () => ()
      case Some(toUpdate) =>
        val followUps = toUpdate map (_.get map (_()) getOrElse (() => ()))

      { () =>
        followUps foreach (_())
      }
    }
  }

  override def now: A = current
}

class EitherSplit[A, B, C](from: Target[Either[A, B]], left: Target[A] => C, right: Target[B] => C) extends ComputedTarget[C] {
  private class StoredLeft(init: A) extends ComputedTarget[A] {
    var last: A = init

    override protected def compute() = {
      from.rely(currentObserving, () => () => ()) match {
        case Left(a) =>
          last = a
          a
        case _ => last
      }
    }
  }

  private class StoredRight(init: B) extends ComputedTarget[B] {
    var last: B = init

    override protected def compute() = {
      from.rely(currentObserving, () => () => ()) match {
        case Right(b) =>
          last = b
          b
        case _ => last
      }
    }
  }

  private var storedTarget: Option[(Either[StoredLeft, StoredRight], C)] = None

  override protected def compute(): C = {
    def fullyUpset(): (() => Unit) = {
      val first = upset()

      val next =
        storedTarget match {
          case Some((Left(t), _)) => t.upset()
          case Some((Right(t), _)) => t.upset()
          case _ => () => ()
        }

      { () =>
        first()
        next()
      }
    }

    from.rely(currentObserving, fullyUpset) match {
      case Left(a) =>
        storedTarget match {
          case Some((Left(storedLeft), c)) => c
          case _ =>
            val storedLeft = new StoredLeft(a)
            val c = left(storedLeft)
            storedTarget = Some((Left(storedLeft), c))
            c
        }
      case Right(b) =>
        storedTarget match {
          case Some((Right(storedRight), c)) => c
          case _ =>
            val storedRight = new StoredRight(b)
            val c = right(storedRight)
            storedTarget = Some((Right(storedRight), c))
            c
        }
    }
  }
}

class FunctionSplit[A,B,C](from: Target[A], f: A => B, g: (B, Target[A]) => C) extends ComputedTarget[C] {
  private case class Stored(key: B) extends ComputedTarget[A] {
    override protected def compute(): A =
      from.rely(currentObserving, () => () => ())
  }

  private var stored: Option[(Stored, C)] = None

  override protected def compute(): C = {
    def fullyUpset(): (() => Unit) = {
      val first = upset()

      val next =
        stored match {
          case Some((t, _)) => t.upset()
          case _ => () => ()
        }

      { () =>
        first()
        next()
      }
    }

    val current = from.rely(currentObserving, fullyUpset)
    val currentKey = f(current)
    stored match {
      case Some((Stored(`currentKey`), c)) => c
      case _ =>
        val nextStored = Stored(currentKey)
        val c = g(currentKey, nextStored)
        stored = Some((nextStored, c))
        c
    }
  }
}

class SeqFunctionSplit[A,B,C](from: Target[A], f: A => Seq[B], g: (B, Target[A]) => C) extends ComputedTarget[Seq[C]] {
  private class Stored(val key: B, _c: => C) extends ComputedTarget[A] {
    lazy val c = _c

    override protected def compute(): A =
      from.rely(currentObserving, () => () => ())
  }

  private var stored: Map[B, Stored] = Map()

  override protected def compute(): Seq[C] = {
    def fullyUpset(): (() => Unit) = {
      val first = upset()

      val next =
        (stored.values map (_.upset())).toSeq

      { () =>
        first()
        next foreach (_())
      }
    }

    val current = from.rely(currentObserving, fullyUpset)
    val currentKeySeq = f(current)

    val nextStoredSeq =
      currentKeySeq map { key =>
        stored.getOrElse(key, {
          lazy val nextStored: Stored = new Stored(key, c)
          lazy val c: C = g(key, nextStored)
          nextStored
        })
      }

    stored = (nextStoredSeq map (s => (s.key, s))).toMap

    nextStoredSeq map (_.c)
  }
}

class MapSource[K, V](default: V) {
  private val holdingMap = mutable.Map[K, V]()
  private val waiting = mutable.Map[K, List[() => () => Unit]]()
  private val holisticWaiting = mutable.ArrayBuffer[() => () => Unit]()

  def apply(key: K): BiTarget[V] = new ComputedTarget[V] with BiTarget[V] {
    override protected def compute(): V = {
      waiting += ((key, (() => upset()) :: waiting.getOrElse(key, Nil)))
      holdingMap.getOrElse(key, default)
    }

    override def delayedUpdate(value: V) = {
      if (key == default)
        holdingMap -= key
      else
        holdingMap += ((key, value))

      val toNotify = waiting.getOrElse(key, Nil) ++ holisticWaiting.toSeq
      waiting -= key
      holisticWaiting.clear()

      val next = toNotify map (_())

      () =>
        next foreach (_())
    }
  }

  def toMap: Target[Map[K, V]] = new ComputedTarget[Map[K, V]] {
    override protected def compute(): Map[K, V] = {
      holisticWaiting += (() => upset())
      holdingMap.toMap
    }
  }

  def update(key: K, value: V): Unit = {
    if (key == default)
      holdingMap -= key
    else
      holdingMap += ((key, value))

    val toNotify = waiting.getOrElse(key, Nil) ++ holisticWaiting.toSeq
    waiting -= key
    holisticWaiting.clear()

    val next = toNotify map (_())
    next foreach (_())
  }
}

class IdentityRoot[A](key: Target[A]) {
  val hub = new IdentityHub(key)
  def ~(against: A) = new ComputedTarget[Boolean] {
    override protected def compute(): Boolean = hub.rely(currentObserving, against, upset)
  }
}

class IdentityHub[A](key: Target[A]) extends UnsafeUpsettable {
  protected var currentObserving = new Observing
  private val listeners = mutable.Map[A, WeakReference[() => () => Unit]]()
  private var current: A = key.now

  def rely(obs: ObservingLike, against: A, changed: () => () => Unit): Boolean = {
    obs.observe(changed)
    listeners(against) = WeakReference(changed)
    key.rely(currentObserving, upset) == against
  }

  def upset: () => () => Unit = ComputedTarget.weakUpset(this)

  def unsafeUpset(): () => Unit = {
    val next = key.now
    val toNotify = synchronized {
      val t = (listeners get current) ++ (listeners get next)
      listeners -= current
      listeners -= next
      current = next
      t
    }

    val followUps = toNotify map (_.get map (_()) getOrElse (() => ()))

    { () =>
      followUps foreach (_())

      if (listeners.nonEmpty) {
        key.rely(currentObserving, upset)
      }
    }
  }
}

class ThrottledByAcceptance[A](source: Target[A], accepted: Target[Int]) extends ComputedTarget[(A, Int)] with Observer {
  private var sentVersion: Int = 0
  private var receivedVersion: Int = 0
  private var extraChanges: Boolean = false

  private def inAcceptance = receivedVersion >= sentVersion

  override protected def compute(): (A, Int) = {
    if (inAcceptance) {
      val a = source.rely(currentObserving, () => {
        extraChanges = true
        if (inAcceptance) {
          upset()
        }
        else {
          () => ()
        }
      })
      sentVersion = receivedVersion + 1
      extraChanges = false
      (a, sentVersion)
    }
    else {
      val a = source.now
      (a, sentVersion)
    }
  }

  accepted foreach { acceptedVersion =>
    receivedVersion = acceptedVersion
    if (extraChanges) {
      extraChanges = false
      upset()()
    }
  }
}

trait ForeachNesting {
  def ifNeeded[A](t: Target[A])(handler: A => Unit)
}

object AsNeededForeach {
  private class UpsettingNode[X](source: Target[X]) extends ComputedTarget[X] {
    override protected def compute(): X = source.rely(currentObserving, upset)
    def isDefined = current.isDefined
  }

  def run[A](parentTarget: Target[A])(handler: (A, ForeachNesting) => Unit)(implicit obs: ObservingLike): Unit = {
    obs.observe(handler)
    obs.observe(parentTarget)
    runWeak(parentTarget)(WeakReference(handler))(obs)
  }

  def runWeak[A](sig: Target[A])(f: WeakReference[(A, ForeachNesting) => Unit])(implicit obs: ObservingLike): Unit = {
    val childMap = mutable.Map[Target[_], UpsettingNode[_]]()

    val observing = Var[Observing](new Observing)
    obs.observe(observing)
    var upset: Boolean = true

    def go() {
      upset = false

      f.get foreach { f =>
        val next = sig.rely(observing.it, changed)
        f(next, new ForeachNesting {
          override def ifNeeded[X](childSig: Target[X])(childF: (X) => Unit): Unit = {
            childMap.get(childSig) match {
              case Some(node) =>
                if (!node.isDefined) {
                  childF(node.rely(observing.it, changed).asInstanceOf[X])
                }

              case None =>
                val node = new UpsettingNode[X](childSig)
                childMap(childSig) = node
                childF(node.rely(observing.it, changed))
            }
          }
        })
      }
    }
    lazy val changed = () => {
      if (!upset) {
        upset = true
        () => {
          observing.it = new Observing
          go()
        }
      }
      else {
        () => ()
      }
    }

    go()
  }
}

case class Var[T](var it: T)

class GiveUpWhenDone[A](source: Target[MaybeFinished[A]]) extends ComputedTarget[A] {
  private var finished: Boolean = false

  override protected def compute(): A = {
    source.rely(currentObserving, upset) match {
      case NotFinished(it) => it
      case Finished(it) =>
        synchronized(finished = true)
        it
    }
  }

  override def unsafeUpset(): () => Unit = {
    if (synchronized(finished)) {
      () => ()
    }
    else {
      super.unsafeUpset()
    }
  }
}
