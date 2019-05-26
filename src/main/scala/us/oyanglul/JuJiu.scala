package us.oyanglul.jujiu
import cats.data.Kleisli
import cats.effect.{ Sync }
import com.github.benmanes.caffeine.cache.{
  Cache => CCache
}

trait CaffeineCache[F[_], K, V] extends Cache[F, CCache[K, V], K, V]{
  type Ops[A] = Kleisli[F, CCache[K, V], A]

  def put(k: K, v: V)(implicit M: Sync[F]): Ops[Unit] =
    Kleisli{caffeine =>M.delay(caffeine.put(k, v)) }
  def fetch(k: K)(implicit M: Sync[F]): Ops[Option[V]] =
    Kleisli(caffeine => M.delay(Option(caffeine.getIfPresent(k))))
  def clear(k: K)(implicit M: Sync[F]): Ops[Unit] =
    Kleisli(caffeine => M.delay(caffeine.invalidate(k)))
}

trait Cache[F[_], S, K, V] {
  def put(k: K, v: V)(implicit M: Sync[F]): Kleisli[F, S, Unit]
  def fetch(k: K)(implicit M: Sync[F]): Kleisli[F, S, Option[V]]
  def fetch(k: K, default: K => F[V])(implicit M: Sync[F]): Kleisli[F, S, V] =
    fetch(k).flatMapF(_ match {
      case Some(v) => M.delay(v)
      case None => default(k)
    })
  def clear(k: K)(implicit M: Sync[F]): Kleisli[F, S, Unit]
}
