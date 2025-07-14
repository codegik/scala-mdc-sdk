package com.codegik.mdc.client.transport

import com.codegik.mdc.client.McpStreamImpl
import com.codegik.mdc.spec.{McpClientTransport, McpTransportSession}
import com.codegik.mdc.util.Assert

import java.io.{BufferedReader, BufferedWriter, InputStreamReader, OutputStreamWriter}
import java.util.UUID
import java.util.concurrent.{ConcurrentHashMap, Executors}
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

/**
 * Implementation of McpClientTransport using standard I/O.
 */
class StdioClientTransport(implicit val executionContext: ExecutionContext) extends McpClientTransport {
  private val id = UUID.randomUUID().toString
  private val sessions = new ConcurrentHashMap[String, McpTransportSession]()
  private var initialized = false
  private var reader: BufferedReader = _
  private var writer: BufferedWriter = _
  private val executor = Executors.newSingleThreadExecutor()

  /**
   * Gets the unique identifier for this transport.
   *
   * @return The transport identifier
   */
  override def getId: String = id

  /**
   * Initializes the client transport.
   *
   * @return A Future that completes when initialization is done
   */
  override def initialize(): Future[Unit] = {
    Future {
      if (!initialized) {
        reader = new BufferedReader(new InputStreamReader(System.in))
        writer = new BufferedWriter(new OutputStreamWriter(System.out))
        initialized = true

        // Start reading from stdin in a separate thread
        executor.submit(new Runnable {
          override def run(): Unit = {
            try {
              var line: String = null
              while ({line = reader.readLine(); line != null}) {
                val sessionId = extractSessionId(line)
                val session = sessions.get(sessionId)
                if (session != null) {
                  session.send(line)
                }
              }
            } catch {
              case e: Exception =>
                e.printStackTrace()
            }
          }
        })
      }
    }
  }

  /**
   * Sends a request to the server.
   *
   * @param session The transport session
   * @param payload The request payload
   * @return A Future with the response
   */
  override def request(session: McpTransportSession, payload: String): Future[String] = {
    Assert.notNull(session, "Session cannot be null")
    Assert.hasText(payload, "Payload cannot be null or empty")

    if (!initialized) {
      return Future.failed(new IllegalStateException("Transport not initialized"))
    }

    sessions.putIfAbsent(session.getId, session)

    val promise = Promise[String]()

    // Send the payload
    Future {
      writer.write(payload)
      writer.newLine()
      writer.flush()
    }.onComplete {
      case Success(_) =>
        // Set up a subscription to receive the response
        val responseStream = session.getIncomingMessages
        responseStream.subscribe(
          response => {
            promise.trySuccess(response)
            // We only want the first response for request/response pattern
          },
          error => promise.tryFailure(error),
          () => {
            if (!promise.isCompleted) {
              promise.tryFailure(new IllegalStateException("Stream completed without response"))
            }
          }
        )

      case Failure(e) => promise.tryFailure(e)
    }

    promise.future
  }

  /**
   * Establishes a streaming connection with the server.
   *
   * @param session The transport session
   * @param payload The request payload
   * @return A stream of responses
   */
  override def stream(session: McpTransportSession, payload: String): McpStreamImpl[String] = {
    Assert.notNull(session, "Session cannot be null")
    Assert.hasText(payload, "Payload cannot be null or empty")

    if (!initialized) {
      val failedStream = new McpStreamImpl[String]()
      failedStream.error(new IllegalStateException("Transport not initialized"))
      return failedStream
    }

    sessions.putIfAbsent(session.getId, session)
    val resultStream = new McpStreamImpl[String]()

    // Send the payload
    Future {
      writer.write(payload)
      writer.newLine()
      writer.flush()
    }.onComplete {
      case Success(_) =>
        // Set up a subscription to receive responses
        val responseStream = session.getIncomingMessages
        responseStream.subscribe(
          response => resultStream.next(response),
          error => resultStream.error(error),
          () => resultStream.complete()
        )

      case Failure(e) => resultStream.error(e)
    }

    resultStream
  }

  /**
   * Closes the transport.
   *
   * @return A Future that completes when the transport is closed
   */
  override def close(): Future[Unit] = {
    Future {
      if (initialized) {
        executor.shutdown()
        sessions.values().asScala.foreach(_.close())
        sessions.clear()
        reader.close()
        writer.close()
        initialized = false
      }
    }
  }

  /**
   * Extracts the session ID from a message.
   * This is a simplified implementation - in a real implementation,
   * this would parse JSON and extract the session ID from the payload.
   *
   * @param message The message
   * @return The extracted session ID
   */
  private def extractSessionId(message: String): String = {
    // Simplified - would parse JSON in real implementation
    sessions.keys().asScala.find(id => message.contains(id)).orNull
  }
}
