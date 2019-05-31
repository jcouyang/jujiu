package us.oyanglul.jujiu

import cats._
import cats.data.Kleisli
import cats.effect.{Async}
import cats.syntax.parallel._
import cats.syntax.traverse._

trait Cache[F[_], S[_, _], K, V] {
  def put(k: K, v: V)(implicit M: Async[F]): Kleisli[F, S[K, V], Unit]
  def fetch(k: K)(implicit M: Async[F]): Kleisli[F, S[K, V], Option[V]]
  def fetch(k: K, load: K => F[V])(implicit M: Async[F]): Kleisli[F, S[K, V], V] =
    for {
      optional <- fetch(k)
      value <- optional match {
        case Some(v) => Kleisli.pure[F, S[K, V], V](v)
        case None =>
          for {
            backup <- Kleisli.liftF(load(k))
            v <- put(k, backup).map(_ => backup)
          } yield v
      }
    } yield value

  def fetchAll[L[_]: Traverse](keys: L[K])(implicit M: Async[F]): Kleisli[F, S[K, V], L[Option[V]]] =
    keys.traverse(k => fetch(k))

  def parFetchAll[L[_]: Traverse, G[_]](keys: L[K])(implicit M: Async[F], ev: Parallel[F, G]): Kleisli[F, S[K, V], L[Option[V]]] =
    keys.parTraverse(fetch)

  def clear(k: K)(implicit M: Async[F]): Kleisli[F, S[K, V], Unit]
}

trait LoadingCache[F[_], S[_, _], K, V] {
  def fetch(k: K)(implicit M: Async[F]): Kleisli[F, S[K, V], V]
  def fetchAll[L[_]: Traverse](keys: L[K])(implicit M: Async[F]): Kleisli[F, S[K, V], L[V]] =
    keys.traverse(fetch)
  def parFetchAll[L[_]: Traverse, G[_]](keys: L[K])(implicit M: Async[F], ev: Parallel[F, G]): Kleisli[F, S[K, V], L[V]] =
    keys.parTraverse(fetch)
}
