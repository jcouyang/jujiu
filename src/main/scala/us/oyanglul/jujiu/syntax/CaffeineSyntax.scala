package us.oyanglul.jujiu
package syntax

import java.util.concurrent.{CompletableFuture, Executor}

import scala.concurrent.Promise
import scala.concurrent.duration.FiniteDuration
import scala.compat.java8.FutureConverters.{toJava => toJavaFuture}
import scala.compat.java8.DurationConverters.toJava
import scala.util.{Left, Right}
import scala.concurrent.duration._
import cats.effect._

trait CaffeineSyntax {
  import com.github.benmanes.caffeine.cache
  import com.github.benmanes.caffeine.cache._
  implicit class CaffeineWrapper[K, V](caf: Caffeine[K,V]) {
    def expireAfterAccess(duration: FiniteDuration): Caffeine[K, V] =
      caf.expireAfterAccess(toJava(duration))
    def expireAfterWrite(duration: FiniteDuration): Caffeine[K, V] =
      caf.expireAfterWrite(toJava(duration))
    def refreshAfterWrite(duration: FiniteDuration): Caffeine[K, V] =
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
        def asyncLoad(key: KK, executor: Executor): CompletableFuture[VV] = {
          val p = Promise[VV]
          Effect[F].runAsync(load(key)) {
            case Right(v) => IO(p.success(v))
            case Left(e) => IO(p.failure(e))
          }.unsafeRunSync()
          toJavaFuture(p.future).toCompletableFuture
        }
      })
    }
  }
}
