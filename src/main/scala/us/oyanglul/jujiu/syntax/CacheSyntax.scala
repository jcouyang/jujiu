package us.oyanglul.jujiu
package syntax

import cats.{Parallel, Traverse}
import cats.effect._

trait CacheSyntax {
  implicit class SyncCachSyntax[F[_], S[_, _], K, V](dsl: Cache[F, S, K, V])(implicit cacheProvider: S[K, V]) {
    def putF(k: K, v: V)(implicit M: Async[F]): F[Unit] =
      dsl.put(k, v).run(cacheProvider)
    def fetchF(k: K)(implicit M: Async[F]): F[Option[V]] =
      dsl.fetch(k).run(cacheProvider)
    def fetchF(k: K, load: K => F[V])(implicit M: Async[F]): F[V] =
      dsl.fetch(k, load).run(cacheProvider)
    def fetchAllF[L[_]: Traverse](keys: L[K])(implicit M: Async[F]): F[L[Option[V]]] =
      dsl.fetchAll(keys).run(cacheProvider)
    def parFetchAllF[L[_]: Traverse, G[_]](keys: L[K])(implicit M: Async[F], ev: Parallel[F]): F[L[Option[V]]] =
      dsl.parFetchAll[L, G](keys).run(cacheProvider)
    def clearF(k: K)(implicit M: Async[F]): F[Unit] =
      dsl.clear(k).run(cacheProvider)
  }
}

trait LoadingCacheSyntax {
  implicit class LoadingCachSyntax[F[_], S[_, _], K, V](dsl: LoadingCache[F, S, K, V])(implicit
    cacheProvider: S[K, V]
  ) {
    def fetchF(k: K)(implicit M: Async[F]): F[V] =
      dsl.fetch(k).run(cacheProvider)
    def fetchAllF[L[_]: Traverse](keys: L[K])(implicit M: Async[F]): F[L[V]] =
      dsl.fetchAll[L](keys).run(cacheProvider)
    def parFetchAllF[L[_]: Traverse, G[_]](keys: L[K])(implicit M: Async[F], ev: Parallel[F]): F[L[V]] =
      dsl.parFetchAll[L, G](keys).run(cacheProvider)
  }
}
