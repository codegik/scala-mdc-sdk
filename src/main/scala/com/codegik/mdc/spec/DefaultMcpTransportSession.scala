package com.codegik.mdc.spec

import com.codegik.mdc.client.{McpStream, McpStreamImpl}

import scala.concurrent.{ExecutionContext, Future, Promise}

/**
 * Default implementation of McpTransportSession.
 *
 * @param id The unique session ID
 * @param executionContext Execution context for async operations
 */
class DefaultMcpTransportSession(private val id: String)(implicit val executionContext: ExecutionContext) extends McpTransportSession {
  private val messageStream = new McpStreamImpl[String]()
  private var closed = false

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
      messageStream.next(payload)
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
        messageStream.complete()
      }
    }
  }

  /**
   * Gets the stream of incoming messages.
   *
   * @return A stream of incoming messages
   */
  override def getIncomingMessages: McpStream[String] = messageStream
}
