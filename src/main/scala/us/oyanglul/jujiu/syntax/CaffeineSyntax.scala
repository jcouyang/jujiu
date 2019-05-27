package us.oyanglul.jujiu
package syntax

import java.util.concurrent.{CompletableFuture, Executor}

import scala.concurrent.{ExecutionContext, Promise}
import scala.concurrent.duration.FiniteDuration
import scala.compat.java8.FutureConverters.{toJava => toJavaFuture}
import scala.compat.java8.DurationConverters.toJava
import scala.util.{Left, Right}
import scala.concurrent.duration._
import cats.effect._
import com.github.benmanes.caffeine.cache.Caffeine

trait CaffeineSyntax {
  import com.github.benmanes.caffeine.cache
  object Caffeine {
    def apply(): Caffeine[Any, Any] =  cache.Caffeine.newBuilder().asInstanceOf[cache.Caffeine[Any, Any]]
  }
  implicit class CaffeineWrapper[K, V](caf: cache.Caffeine[K, V]) {
    def expireAfterAccess(duration: FiniteDuration): cache.Caffeine[K, V] =
      caf.expireAfterAccess(toJava(duration))
    def expireAfterWrite(duration: FiniteDuration): cache.Caffeine[K, V] =
      caf.expireAfterWrite(toJava(duration))
    def refreshAfterWrite(duration: FiniteDuration): cache.Caffeine[K, V] =
      caf.refreshAfterWrite(toJava(duration))
    def expire[KK <: K, VV <: V](
      afterCreate: (KK, VV) => FiniteDuration,
      afterUpdate: (KK, VV, FiniteDuration) => FiniteDuration,
      afterRead: (KK, VV, FiniteDuration) => FiniteDuration
    ): cache.Caffeine[KK, VV] =
      caf.expireAfter(new cache.Expiry[KK, VV] {
        override def expireAfterCreate(key: KK, value: VV, currentTime: Long): Long =
          afterCreate(key, value).toNanos

        override def expireAfterUpdate(key: KK, value: VV, currentTime: Long, currentDuration: Long): Long =
          afterUpdate(key, value, currentDuration.nanos).toNanos

        override def expireAfterRead(key: KK, value: VV, currentTime: Long, currentDuration: Long): Long =
          afterRead(key, value, currentDuration.nanos).toNanos
      })

    def executionContext(ec: ExecutionContext): cache.Caffeine[K, V] =
      caf.executor(new Executor {
        def execute(r: Runnable) =
          ec.execute(r)
      })
    def sync[KK, VV]: cache.Cache[KK, VV] =
      caf.build().asInstanceOf[cache.Cache[KK, VV]]
    def async[KK <: K, VV <: V]: cache.AsyncCache[KK, VV] =
      caf.buildAsync[KK, VV]()
    def async[F[_]: Effect, KK <: K, VV <: V](load: KK => F[VV]): cache.AsyncLoadingCache[KK, VV] =
      caf.buildAsync(new cache.AsyncCacheLoader[KK, VV] {
        def asyncLoad(key: KK, executor: Executor): CompletableFuture[VV] = {
          val p = Promise[VV]
          Effect[F]
            .runAsync(load(key)) {
              case Right(v) => IO(p.success(v))
              case Left(e)  => IO(p.failure(e))
            }
            .unsafeRunSync()
          toJavaFuture(p.future).toCompletableFuture
        }
      })
  }
}
