package com.codegik.mdc.client

import scala.collection.mutable.ListBuffer

/**
 * Interface for MCP stream.
 * A reactive stream that emits elements of type T.
 */
trait McpStream[T] {
  /**
   * Subscribes to the stream with handlers for next, error, and completion events.
   *
   * @param onNext Handler for next events
   * @param onError Handler for error events
   * @param onComplete Handler for completion events
   */
  def subscribe(onNext: T => Unit, onError: Throwable => Unit, onComplete: () => Unit): Unit

  /**
   * Maps the elements of this stream to another type.
   *
   * @param f The mapping function
   * @return A new stream with mapped elements
   */
  def map[R](f: T => R): McpStream[R]

  /**
   * Filters the elements of this stream.
   *
   * @param predicate The filter predicate
   * @return A new stream with filtered elements
   */
  def filter(predicate: T => Boolean): McpStream[T]
}

/**
 * Implementation of McpStream.
 */
class McpStreamImpl[T] extends McpStream[T] {
  private val subscribers = ListBuffer[(T => Unit, Throwable => Unit, () => Unit)]()
  private var completed = false
  private var error: Option[Throwable] = None

  /**
   * Subscribes to the stream with handlers for next, error, and completion events.
   *
   * @param onNext Handler for next events
   * @param onError Handler for error events
   * @param onComplete Handler for completion events
   */
  override def subscribe(onNext: T => Unit, onError: Throwable => Unit, onComplete: () => Unit): Unit = {
    synchronized {
      if (completed) {
        onComplete()
        return
      }

      if (error.isDefined) {
        onError(error.get)
        return
      }

      subscribers += ((onNext, onError, onComplete))
    }
  }

  /**
   * Maps the elements of this stream to another type.
   *
   * @param f The mapping function
   * @return A new stream with mapped elements
   */
  override def map[R](f: T => R): McpStream[R] = {
    val result = new McpStreamImpl[R]()
    subscribe(
      value => result.next(f(value)),
      error => result.error(error),
      () => result.complete()
    )
    result
  }

  /**
   * Filters the elements of this stream.
   *
   * @param predicate The filter predicate
   * @return A new stream with filtered elements
   */
  override def filter(predicate: T => Boolean): McpStream[T] = {
    val result = new McpStreamImpl[T]()
    subscribe(
      value => if (predicate(value)) result.next(value),
      error => result.error(error),
      () => result.complete()
    )
    result
  }

  /**
   * Emits a value to all subscribers.
   *
   * @param value The value to emit
   */
  def next(value: T): Unit = {
    synchronized {
      if (completed || error.isDefined) return

      subscribers.foreach { case (onNext, _, _) =>
        try {
          onNext(value)
        } catch {
          case _: Exception => // Ignore exceptions in subscriber callbacks
        }
      }
    }
  }

  /**
   * Emits an error to all subscribers.
   *
   * @param throwable The error to emit
   */
  def error(throwable: Throwable): Unit = {
    synchronized {
      if (completed || error.isDefined) return

      error = Some(throwable)
      subscribers.foreach { case (_, onError, _) =>
        try {
          onError(throwable)
        } catch {
          case _: Exception => // Ignore exceptions in subscriber callbacks
        }
      }
      subscribers.clear()
    }
  }

  /**
   * Completes the stream.
   */
  def complete(): Unit = {
    synchronized {
      if (completed || error.isDefined) return

      completed = true
      subscribers.foreach { case (_, _, onComplete) =>
        try {
          onComplete()
        } catch {
          case _: Exception => // Ignore exceptions in subscriber callbacks
        }
      }
      subscribers.clear()
    }
  }
}
