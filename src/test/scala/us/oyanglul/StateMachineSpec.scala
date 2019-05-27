package us.oyanglul.jujiu
import cats.effect.IO
import com.github.benmanes.caffeine.cache
import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.commands.Commands

import scala.collection.Map
import scala.util.{Success, Try}
import syntax.caffeine._
import Arbitrary._
import cats.instances.all._

import scala.concurrent.ExecutionContext

object StateMachineSpec extends org.scalacheck.Properties("Jujiu StateMachine") {

  property("Caffeine sync cache") = new CaffeineCacheSpec[Int, Int].property()
  property("Caffeine async cache") = new CaffeineAsyncCacheSpec[Int, Int].property()

}
class CaffeineCacheSpec[K: Arbitrary, V : Arbitrary] extends CaffeineCacheBaseSpec[K,V] {
  type Subject[A, B] = cache.Cache[A, B]
  val dsl: Cache[IO, Subject, K, V] = new CaffeineCache[IO, K, V] {}
  def newSut(state: State): Sut =
    Caffeine()
      .sync
  def destroySut(sut: Sut): Unit = sut.cleanUp()
  def genCommand(state: State): Gen[Command] =
    Gen.oneOf(
      genFetch,
      genPut,
      genFetchOr,
      genClear
    )
}

abstract class CaffeineCacheBaseSpec[K :Arbitrary, V : Arbitrary] extends Commands {
  type State = Map[K, V]

  type Sut = Subject[K, V]

  type Subject[A, B]
  val dsl: Cache[IO, Subject, K, V]

  def initialPreCondition(state: State): Boolean = true
  def genInitialState: Gen[State] = Map[K, V]()
  def newSut(state: State): Sut
  def canCreateNewSut(newState: State, initSuts: Traversable[State], runningSuts: Traversable[Sut]): Boolean = true
  def destroySut(sut: Sut): Unit

  def genCommand(state: State): Gen[Command]

  def genFetch = arbitrary[K].map(Fetch)
  def genFetchAll = arbitrary[List[K]].map(FetchAll)
  def genClear = arbitrary[K].map(Clear)

  def genFetchOr =
    for {
      k <- arbitrary[K]
      v <- arbitrary[V]
    } yield FetchOr(k, _ => IO(v))
  def genPut =
    for {
      k <- arbitrary[K]
      v <- arbitrary[V]
    } yield Put(k, v)

  case class Fetch(key: K) extends Command {
    type Result = Option[V]
    def run(sut: Sut) = dsl.fetch(key).run(sut).unsafeRunSync()
    def preCondition(state: State) = true
    def nextState(state: State) = state
    def postCondition(state: State, result: Try[Result]) =
      result == Success(state.get(key))
  }

  case class FetchOr(key: K, load: K => IO[V]) extends Command {
    type Result = V
    val default = load(key).unsafeRunSync()
    def run(sut: Sut) = dsl.fetch(key, load).run(sut).unsafeRunSync()
    def preCondition(state: State) = true
    def nextState(state: State) = if (state.contains(key)) state else state + (key -> default)
    def postCondition(state: State, result: Try[Result]) =
      result == Success(state.getOrElse(key, default))
  }

  case class Put(key: K, value: V) extends Command {
    type Result = Unit
    def run(sut: Sut) = dsl.put(key, value).run(sut).unsafeRunSync()
    def preCondition(state: State) = true
    def nextState(state: State) = state + (key -> value)
    def postCondition(state: State, result: Try[Result]) =
      result == Success(())
  }

  case class Clear(key: K) extends Command {
    type Result = Unit
    def run(sut: Sut) = dsl.clear(key).run(sut).unsafeRunSync()
    def preCondition(state: State) = true
    def nextState(state: State) = state - key
    def postCondition(state: State, result: Try[Result]) =
      result == Success(())
  }

  case class FetchAll(keys: List[K]) extends Command {
    type Result = List[Option[V]]
    def run(sut: Sut) = dsl.fetchAll(keys).run(sut).unsafeRunSync()
    def preCondition(state: State) = true
    def nextState(state: State) = state
    def postCondition(state: State, result: Try[Result]) =
      result == Success(keys.map(state.get(_)))
  }

}

class CaffeineAsyncCacheSpec[K: Arbitrary, V: Arbitrary] extends CaffeineCacheBaseSpec[K, V] {
  type Subject[A, B] = cache.AsyncCache[A, B]
  val dsl: Cache[IO, Subject, K, V] = new CaffeineAsyncCache[IO, K, V] {
    implicit val executionContext = ExecutionContext.global
  }
  def newSut(state: State): Sut =
    Caffeine()
      .async

  def genCommand(state: State): Gen[Command] =
    Gen.oneOf(
      genFetch,
      genPut,
      genFetchOr
    )
  def destroySut(sut: Sut): Unit = (())
}
