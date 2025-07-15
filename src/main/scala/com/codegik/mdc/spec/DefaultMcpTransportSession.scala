package com.codegik.mdc.spec

import com.codegik.mdc.client.McpStream

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.collection.mutable.ArrayBuffer

/**
 * Default implementation of McpTransportSession.
 *
 * @param id The unique session ID
 * @param executionContext Execution context for async operations
 */
class DefaultMcpTransportSession(private val id: String)(implicit val executionContext: ExecutionContext) extends McpTransportSession {
  // Buffer to store messages and callback registry
  private val messageBuffer = ArrayBuffer[String]()
  private val callbacks = ArrayBuffer[(String) => Unit]()
  private var closed = false
  private val streamPromise = Promise[LazyList[String]]()

  // Create the lazy list from a custom state function that will be updated as new messages arrive
  private val lazyMessages: LazyList[String] = LazyList.unfold(0) { index =>
    if (index < messageBuffer.size) {
      Some((messageBuffer(index), index + 1))
    } else if (closed) {
      None // End of stream
    } else {
      // Wait for more messages (this will effectively pause the LazyList evaluation)
      Thread.sleep(10)
      Some((messageBuffer.lastOption.getOrElse(""), index))
    }
  }

  /**
   * Gets the unique session ID.
   *
   * @return The session ID
   */
  override def getId: String = id

  /**
   * Sends a message to the other endpoint.
   *
   * @param payload The message payload
   * @return A Future that completes when the message is sent
   */
  override def send(payload: String): Future[Unit] = {
    if (closed) {
      return Future.failed(new IllegalStateException(s"Session $id is closed"))
    }

    Future {
      // Add to buffer and notify all registered callbacks
      synchronized {
        messageBuffer.append(payload)
        callbacks.foreach(callback => callback(payload))
      }
    }
  }

  /**
   * Closes the session.
   *
   * @return A Future that completes when the session is closed
   */
  override def close(): Future[Unit] = {
    Future {
      if (!closed) {
        closed = true
        // Complete the stream promise if not already done
        if (!streamPromise.isCompleted) {
          streamPromise.success(LazyList.from(messageBuffer))
        }
      }
    }
  }

  /**
   * Gets the stream of incoming messages.
   *
   * @return A stream of incoming messages
   */
  override def getIncomingMessages: LazyList[String] = {
    lazyMessages
  }

  /**
   * Subscribes to incoming messages with callback functions.
   * This method provides backward compatibility with the previous streaming approach.
   *
   * @param onNext Callback for new messages
   * @param onError Callback for errors
   * @param onComplete Callback for stream completion
   */
  def subscribe(onNext: String => Unit, onError: Throwable => Unit, onComplete: () => Unit): Unit = {
    synchronized {
      // Register the callback for future messages
      callbacks.append(onNext)

      // Process existing messages
      messageBuffer.foreach(msg => onNext(msg))

      // Set up completion notification
      if (closed) {
        onComplete()
      }
    }
  }
}
