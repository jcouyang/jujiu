package us.oyanglul.jujiu

import java.util.concurrent.CompletableFuture

import cats.data.Kleisli
import cats.effect.{Async}
import cats.instances.all._
import cats.syntax.all._
import com.github.benmanes.caffeine.cache.{
  AsyncCache => CACache,
  AsyncLoadingCache => CALCache,
  Cache => CCache,
  LoadingCache => CLCache
}

import scala.compat.java8.FutureConverters.toScala
import scala.concurrent.ExecutionContext
import scala.util._

trait CaffeineCache[F[_], K, V] extends Cache[F, CCache, K, V] {
  def put(k: K, v: V)(implicit M: Async[F]): Kleisli[F, CCache[K, V], Unit] =
    Kleisli { caffeine =>
      M.delay(caffeine.put(k, v))
    }
  def fetch(k: K)(implicit M: Async[F]): Kleisli[F, CCache[K, V], Option[V]] =
    Kleisli(caffeine => M.delay(Option(caffeine.getIfPresent(k))))
  def clear(k: K)(implicit M: Async[F]): Kleisli[F, CCache[K, V], Unit] =
    Kleisli(caffeine => M.delay(caffeine.invalidate(k)))
  def clearAll(implicit M: Async[F]): Kleisli[F, CCache[K, V], Unit] =
    Kleisli(caffeine => M.delay(caffeine.invalidateAll))
}

trait CaffeineAsyncCache[F[_], K, V] extends Cache[F, CACache, K, V] {
  implicit val executionContext: ExecutionContext
  def put(k: K, v: V)(implicit M: Async[F]): Kleisli[F, CACache[K, V], Unit] =
    Kleisli { caffeine =>
      M.delay(caffeine.put(k, CompletableFuture.completedFuture(v)))
    }
  def fetch(k: K)(implicit M: Async[F]): Kleisli[F, CACache[K, V], Option[V]] =
    Kleisli(
      caffeine =>
        M.async { cb =>
          Option(caffeine.getIfPresent(k)).map(toScala).sequence.onComplete {
            case Success(v) => cb(Right(v))
            case Failure(e) => cb(Left(e))
          }
        }
    )
  def clear(k: K)(implicit M: Async[F]): Kleisli[F, CACache[K, V], Unit] =
    Kleisli(caffeine => M.delay(caffeine.synchronous().invalidate(k)))
  def clearAll(implicit M: Async[F]): Kleisli[F, CACache[K, V], Unit] =
    Kleisli(caffeine => M.delay(caffeine.synchronous().invalidateAll))
}

trait CaffeineAsyncLoadingCache[F[_], K, V] extends LoadingCache[F, CALCache, K, V] {
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
trait CaffeineLoadingCache[F[_], K, V] extends LoadingCache[F, CLCache, K, V] {
  def fetch(k: K)(implicit M: Async[F]): Kleisli[F, CLCache[K, V], V] =
    Kleisli(caffeine => M.delay(caffeine.get(k)))
}
