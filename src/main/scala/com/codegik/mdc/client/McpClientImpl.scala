package com.codegik.mdc.client

import com.codegik.mdc.spec.{DefaultJsonSchemaValidator, DefaultMcpTransportSession, JsonSchemaValidator, McpClientSession, McpClientTransport, McpTransportSession}
import com.codegik.mdc.util.Assert
import com.codegik.mdc.spec._
import scala.concurrent.ExecutionContext.Implicits.global

import java.util.UUID
import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

/**
 * Implementation of McpAsyncClient.
 *
 * @param transport The client transport to use
 * @param features Client features configuration
 * @param validator JSON schema validator
 * @param executionContext Execution context for async operations
 */
class McpAsyncClientImpl(
  private val transport: McpClientTransport,
  private val features: McpClientFeatures,
  private val validator: JsonSchemaValidator,
  private implicit val executionContext: ExecutionContext
) extends McpAsyncClient {

  private val sessions = TrieMap.empty[String, McpClientSession]
  private var initialized = false

  /**
   * Initializes the client.
   *
   * @return A Future that completes when initialization is done
   */
  override def initialize(): Future[Unit] = {
    Future {
      if (!initialized) {
        // Block on transport initialization for simplicity
        import scala.concurrent.Await
        import scala.concurrent.duration._
        Await.result(transport.initialize(), 30.seconds)
        initialized = true
      }
    }
  }

  /**
   * Sends a request asynchronously.
   *
   * @param payload The request payload
   * @return A Future with the response
   */
  override def request(payload: String): Future[String] = {
    Assert.hasText(payload, "Payload cannot be null or empty")
    if (!initialized) {
      return Future.failed(new IllegalStateException("Client not initialized"))
    }

    val sessionId = UUID.randomUUID().toString
    val session = createSession(sessionId)

    // If schema validation is enabled, validate the request
    val validationFuture = if (features.validateRequestSchema) {
      validator.validate(payload, session.getRequestSchema)
    } else {
      Future.successful(())
    }

    val responseFuture = validationFuture.flatMap(_ =>
      transport.request(session.getTransportSession, payload)
    )

    // If schema validation is enabled, validate the response
    val validatedResponseFuture = responseFuture.flatMap { response =>
      if (features.validateResponseSchema) {
        validator.validate(response, session.getResponseSchema).map(_ => response)
      } else {
        Future.successful(response)
      }
    }

    // Clean up session when done
    validatedResponseFuture.onComplete { _ =>
      sessions.remove(sessionId)
      session.getTransportSession.close()
    }

    validatedResponseFuture
  }

  /**
   * Establishes a streaming connection.
   *
   * @param payload The request payload
   * @return A stream of responses
   */
  override def stream(payload: String): LazyList[String] = {
    Assert.hasText(payload, "Payload cannot be null or empty")
    if (!initialized) {
      // Return an empty LazyList for uninitialized client
      return LazyList.empty
    }

    val sessionId = UUID.randomUUID().toString
    val session = createSession(sessionId)

    // If schema validation is enabled, validate the request
    val validationFuture = if (features.validateRequestSchema) {
      validator.validate(payload, session.getRequestSchema)
    } else {
      Future.successful(())
    }

    // Create a buffer to hold stream elements while being processed
    val streamBuffer = scala.collection.mutable.ArrayBuffer[String]()

    // Set up a promise that will complete when the transport stream is ready
    val streamPromise = Promise[LazyList[String]]()

    validationFuture.onComplete {
      case Success(_) =>
        // Start streaming
        val responseStream = transport.stream(session.getTransportSession, payload)

        // Set up a poller that will periodically check for new messages
        val pollingExecutor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
        val pollingTask = new Runnable {
          override def run(): Unit = {
            if (responseStream.nonEmpty) {
              // Process any new elements that have arrived
              val newElements = responseStream.dropWhile(e => streamBuffer.contains(e))
              if (newElements.nonEmpty) {
                // Add new elements to the buffer
                newElements.foreach { response =>
                  // Validate if needed
                  if (features.validateResponseSchema) {
                    validator.validate(response, session.getResponseSchema).onComplete {
                      case Success(_) =>
                        streamBuffer.synchronized {
                          streamBuffer.append(response)
                        }
                      case Failure(e) =>
                        // Skip invalid responses
                    }
                  } else {
                    streamBuffer.synchronized {
                      streamBuffer.append(response)
                    }
                  }
                }
              }
            }
          }
        }

        // Schedule the polling task
        pollingExecutor.scheduleAtFixedRate(pollingTask, 0, 100, java.util.concurrent.TimeUnit.MILLISECONDS)

        // Complete the promise with a LazyList that will read from the buffer
        streamPromise.success(
          LazyList.unfold(0) { index =>
            streamBuffer.synchronized {
              if (index < streamBuffer.size) {
                Some((streamBuffer(index), index + 1))
              } else {
                None
              }
            }
          }
        )

      case Failure(e) =>
        // Complete with an empty stream on validation failure
        streamPromise.success(LazyList.empty)
        sessions.remove(sessionId)
        session.getTransportSession.close()
    }

    // If we can't get a stream right away, return an empty one
    try {
      import scala.concurrent.Await
      import scala.concurrent.duration._
      Await.result(streamPromise.future, 1.second)
    } catch {
      case _: Exception => LazyList.empty
    }
  }

  /**
   * Closes the client.
   *
   * @return A Future that completes when the client is closed
   */
  override def close(): Future[Unit] = {
    Future {
      if (initialized) {
        sessions.values.foreach(session =>
          session.getTransportSession.close()
        )
        sessions.clear()

        // Block on transport close for simplicity
        import scala.concurrent.Await
        import scala.concurrent.duration._
        Await.result(transport.close(), 30.seconds)

        initialized = false
      }
    }
  }

  /**
   * Creates a new client session.
   *
   * @param sessionId The session ID
   * @return The created session
   */
  private def createSession(sessionId: String): McpClientSession = {
    val transportSession = new DefaultMcpTransportSession(sessionId)
    val clientSession = new DefaultMcpClientSession(transportSession)
    sessions.put(sessionId, clientSession)
    clientSession
  }
}

/**
 * Implementation of McpSyncClient.
 * This is a wrapper around McpAsyncClient that blocks on operations.
 *
 * @param asyncClient The async client to wrap
 */
class McpSyncClientImpl(private val asyncClient: McpAsyncClient) extends McpSyncClient {

  import scala.concurrent.Await
  import scala.concurrent.duration._

  private val DefaultTimeout = 30.seconds

  /**
   * Initializes the client.
   *
   * @return A Future that completes when initialization is done
   */
  override def initialize(): Future[Unit] = {
    asyncClient.initialize()
  }

  /**
   * Sends a request synchronously.
   *
   * @param payload The request payload
   * @return The response
   */
  override def request(payload: String): String = {
    Await.result(asyncClient.request(payload), DefaultTimeout)
  }

  /**
   * Establishes a streaming connection and processes the stream with a callback.
   *
   * @param payload The request payload
   * @param callback The callback to process streaming responses
   */
  override def stream(payload: String, callback: String => Unit): Unit = {
    // Get the stream from the async client
    val lazyStream = asyncClient.stream(payload)

    // Process each element with the callback
    lazyStream.foreach(callback)
  }

  /**
   * Closes the client.
   *
   * @return A Future that completes when the client is closed
   */
  override def close(): Future[Unit] = {
    asyncClient.close()
  }
}

/**
 * Default implementation of McpClientSession.
 *
 * @param transportSession The transport session
 */
class DefaultMcpClientSession(private val transportSession: McpTransportSession) extends McpClientSession {
  // In a real implementation, these would be loaded from schema resources
  private val requestSchema = "{}"
  private val responseSchema = "{}"

  /**
   * Gets the transport session.
   *
   * @return The transport session
   */
  override def getTransportSession: McpTransportSession = transportSession

  /**
   * Gets the request schema.
   *
   * @return The request schema
   */
  override def getRequestSchema: String = requestSchema

  /**
   * Gets the response schema.
   *
   * @return The response schema
   */
  override def getResponseSchema: String = responseSchema
}

/**
 * Factory methods for creating MCP clients.
 */
object McpAsyncClient {
  /**
   * Creates a new async client.
   *
   * @param transport The client transport to use
   * @param features Client features configuration
   * @param executionContext Execution context for async operations
   * @return The created client
   */
  def create(transport: McpClientTransport, features: McpClientFeatures = McpClientFeatures.builder().build())
            (implicit executionContext: ExecutionContext = ExecutionContext.global): McpAsyncClient = {
    Assert.notNull(transport, "Transport cannot be null")
    Assert.notNull(features, "Features cannot be null")

    val validator = new DefaultJsonSchemaValidator()
    new McpAsyncClientImpl(transport, features, validator, executionContext)
  }
}

/**
 * Factory methods for creating MCP clients.
 */
object McpSyncClient {
  /**
   * Creates a new sync client.
   *
   * @param transport The client transport to use
   * @param features Client features configuration
   * @param executionContext Execution context for async operations
   * @return The created client
   */
  def create(transport: McpClientTransport, features: McpClientFeatures = McpClientFeatures.builder().build())
            (implicit executionContext: ExecutionContext = ExecutionContext.global): McpSyncClient = {
    Assert.notNull(transport, "Transport cannot be null")
    Assert.notNull(features, "Features cannot be null")

    val asyncClient = McpAsyncClient.create(transport, features)(executionContext)
    new McpSyncClientImpl(asyncClient)
  }
}
