package us.oyanglul.jujiu
import cats._
import cats.data.Kleisli
import cats.effect.{ Async, Effect, IO, Sync }
import cats.syntax.traverse._
import cats.syntax.parallel._
import java.util.concurrent.Executor
import scala.compat.java8.FutureConverters.{toScala, toJava => toJavaFuture}
import scala.compat.java8.DurationConverters.toJava
import com.github.benmanes.caffeine.cache.{Cache => CCache, AsyncLoadingCache => CALCache, LoadingCache => CLCache}
import scala.concurrent.{ ExecutionContext, Promise }
import scala.concurrent.duration._
import scala.util._

trait CaffeineCache[F[_], K, V] extends Cache[F, CCache[K, V], K, V] {
  type Ops[A] = Kleisli[F, CCache[K, V], A]

  def put(k: K, v: V)(implicit M: Sync[F]): Ops[Unit] =
    Kleisli { caffeine =>
      M.delay(caffeine.put(k, v))
    }
  def fetch(k: K)(implicit M: Sync[F]): Ops[Option[V]] =
    Kleisli(caffeine => M.delay(Option(caffeine.getIfPresent(k))))
  def clear(k: K)(implicit M: Sync[F]): Ops[Unit] =
    Kleisli(caffeine => M.delay(caffeine.invalidate(k)))
}

trait CaffeineLoadingCache[F[_], K, V] extends LoadingCache[F, CLCache[K, V], K, V] {
  override def fetch(k: K)(implicit M: Sync[F]): Kleisli[F, CLCache[K, V], V] =
    Kleisli(caffeine => M.delay(caffeine.get(k)))
}

trait CaffeineAsyncLoadingCache[F[_], K, V] extends AsyncLoadingCache[F, CALCache[K, V], K, V] {
  implicit val executionContext: ExecutionContext
  override def fetch(k: K)(implicit M: Async[F]): Kleisli[F, CALCache[K, V], V] =
    Kleisli { caffeine =>
      M.async { cb =>
        val future = toScala(caffeine.get(k))
        future.onComplete {
          case Success(v) => cb(Right(v))
          case Failure(e) => cb(Left(e))
        }
      }
    }
}

trait Cache[F[_], S, K, V] {
  def put(k: K, v: V)(implicit M: Sync[F]): Kleisli[F, S, Unit]
  def fetch(k: K)(implicit M: Sync[F]): Kleisli[F, S, Option[V]]
  def fetch(k: K, default: K => F[V])(implicit M: Sync[F]): Kleisli[F, S, V] =
    fetch(k).flatMapF(_ match {
      case Some(v) => M.delay(v)
      case None    => default(k)
    })
  def fetchAll[L[_]: Traverse](keys: L[K])(implicit M: Sync[F]) =
    keys.traverse(k => fetch(k))

  def clear(k: K)(implicit M: Sync[F]): Kleisli[F, S, Unit]
}

trait LoadingCache[F[_], S, K, V] {
  def fetch(k: K)(implicit M: Sync[F]): Kleisli[F, S, V]
}

trait AsyncLoadingCache[F[_], S, K, V] {
  def fetch(k: K)(implicit M: Async[F]): Kleisli[F, S, V]
  def fetchAll[L[_]: Traverse, G[_]](keys: L[K])(implicit M: Async[F], ev: Parallel[F, G]) =
    keys.parTraverse(fetch)
}

case class CaffeineConfig(
  expireAfter: Option[FiniteDuration] = None,
  expireAfterAccess: Option[FiniteDuration] = None,
  expireAfterWrite: Option[FiniteDuration] = None,
  initialCapacity: Option[Int] = None,
  maximumSize: Option[Long] = None,
  maximumWeight: Option[Long] = None,
  refreshAfterWrite: Option[FiniteDuration] = None,
)
trait CaffeineSyntax {
    import com.github.benmanes.caffeine.cache
  import com.github.benmanes.caffeine.cache._
  implicit class CaffeineWrapper[K, V](caf: Caffeine[K,V]) {
    def expireAfterAccess(duration: FiniteDuration) =
      caf.expireAfterAccess(toJava(duration))
    def expireAfterWrite(duration: FiniteDuration) =
      caf.expireAfterWrite(toJava(duration))
    def refreshAfterWrite(duration: FiniteDuration) =
      caf.refreshAfterWrite(toJava(duration))
    def expireAfter[KK <: K, VV <: V](
     create: (K, V) => FiniteDuration,
      update: (K, V, FiniteDuration) => FiniteDuration,
      read: (K, V, FiniteDuration) => FiniteDuration
    ): Caffeine[K, V] = caf.expireAfter(new Expiry[K, V] {
      override def expireAfterCreate(key: K, value: V, currentTime: Long): Long =
        create(key, value).toNanos

      override def expireAfterUpdate(key: K, value: V, currentTime: Long, currentDuration: Long): Long =
        update(key, value, currentDuration.nanos).toNanos

      override def expireAfterRead(key: K, value: V, currentTime: Long, currentDuration: Long): Long =
        read(key, value, currentDuration.nanos).toNanos
    })

    def async[F[_]: Effect, KK <: K, VV <: V](load: KK => F[VV]): cache.AsyncLoadingCache[KK, VV] = {
      caf.buildAsync(new AsyncCacheLoader[KK, VV] {
        def asyncLoad(key: KK, executor: Executor) = {
          val p = Promise[VV]
          Effect[F].runAsync(load(key)) {
            case Right(v) => IO(p.success(v))
            case Left(e) => IO(p.failure(e))
          }.unsafeRunSync()
          toJavaFuture(p.future).toCompletableFuture()
        }
      })
    }
  }
}
