package us.oyanglul.jujiu

import cats._
import cats.data.Kleisli
import cats.effect.{Async, Sync}
import cats.syntax.parallel._
import cats.syntax.traverse._

trait Cache[F[_], S, K, V] {
  def put(k: K, v: V)(implicit M: Sync[F]): Kleisli[F, S, Unit]
  def fetch(k: K)(implicit M: Sync[F]): Kleisli[F, S, Option[V]]
  def fetch(k: K, default: K => F[V])(implicit M: Sync[F]): Kleisli[F, S, V] =
    fetch(k).flatMapF {
      case Some(v) => M.delay(v)
      case None => default(k)
    }
  def fetchAll[L[_]: Traverse](keys: L[K])(implicit M: Sync[F]): Kleisli[F, S, L[Option[V]]] =
    keys.traverse(k => fetch(k))

  def clear(k: K)(implicit M: Sync[F]): Kleisli[F, S, Unit]
}

trait LoadingCache[F[_], S, K, V] {
  def fetch(k: K)(implicit M: Sync[F]): Kleisli[F, S, V]
}

trait AsyncLoadingCache[F[_], S, K, V] {
  def fetch(k: K)(implicit M: Async[F]): Kleisli[F, S, V]
  def fetchAll[L[_]: Traverse, G[_]](keys: L[K])(implicit M: Async[F], ev: Parallel[F, G]): Kleisli[F, S, L[V]] =
    keys.parTraverse(fetch)
}
