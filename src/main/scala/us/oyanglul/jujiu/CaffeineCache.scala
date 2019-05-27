package us.oyanglul.jujiu

import cats.data.Kleisli
import cats.effect.{Async, Sync}
import com.github.benmanes.caffeine.cache.{
  AsyncLoadingCache => CALCache,
  LoadingCache => CLCache,
  Cache => CCache}

import scala.compat.java8.FutureConverters.toScala
import scala.concurrent.ExecutionContext
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
trait CaffeineLoadingCache[F[_], K, V] extends LoadingCache[F, CLCache[K, V], K, V] {
  override def fetch(k: K)(implicit M: Sync[F]): Kleisli[F, CLCache[K, V], V] =
    Kleisli(caffeine => M.delay(caffeine.get(k)))
}
