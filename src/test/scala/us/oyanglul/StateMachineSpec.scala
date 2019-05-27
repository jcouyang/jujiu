package us.oyanglul.jujiu
import cats.effect.IO
import com.github.benmanes.caffeine.cache
import com.github.benmanes.caffeine.cache.Caffeine
import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.commands.Commands
import scala.collection.Map
import scala.util.{Success, Try}
import syntax.caffeine._
import Arbitrary._
import cats.instances.all._

object StateMachineSpec extends org.scalacheck.Properties("Jujiu StateMachine") {

  property("Caffeine sync cache") = new CaffeineCacheSpec[Int, Int].property()

}

class CaffeineCacheSpec[K: Arbitrary, V: Arbitrary] extends Commands {
  type State = Map[K, V]

  type Sut = cache.Cache[K, V]
  object dsl extends CaffeineCache[IO, K, V]
  val caffeine = Caffeine
    .newBuilder()
    .build()

  def initialPreCondition(state: State): Boolean = true
  def genInitialState: Gen[State] = Map[K, V]()
  def newSut(state: State): Sut =
    Caffeine
      .newBuilder()
      .sync

  def canCreateNewSut(newState: State, initSuts: Traversable[State], runningSuts: Traversable[Sut]): Boolean = true
  def destroySut(sut: Sut): Unit = sut.cleanUp()

  def genCommand(state: State): Gen[Command] =
    Gen.oneOf(
      genFetch,
      genPut,
      genFetchOr,
      genClear
    )

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
