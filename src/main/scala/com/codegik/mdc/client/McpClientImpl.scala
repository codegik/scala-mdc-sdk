package com.codegik.mdc.client

import com.codegik.mdc.spec.{DefaultJsonSchemaValidator, DefaultMcpTransportSession, JsonSchemaValidator, McpClientSession, McpClientTransport, McpTransportSession}
import com.codegik.mdc.util.Assert
import com.codegik.mdc.spec._

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import scala.collection.JavaConverters._
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

  private val sessions = new ConcurrentHashMap[String, McpClientSession]()
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
  override def stream(payload: String): McpStream[String] = {
    Assert.hasText(payload, "Payload cannot be null or empty")
    if (!initialized) {
      val failedStream = new McpStreamImpl[String]()
      failedStream.error(new IllegalStateException("Client not initialized"))
      return failedStream
    }

    val sessionId = UUID.randomUUID().toString
    val session = createSession(sessionId)
    val resultStream = new McpStreamImpl[String]()

    // If schema validation is enabled, validate the request
    val validationFuture = if (features.validateRequestSchema) {
      validator.validate(payload, session.getRequestSchema)
    } else {
      Future.successful(())
    }

    validationFuture.onComplete {
      case Success(_) =>
        // Start streaming
        val responseStream = transport.stream(session.getTransportSession, payload)

        // Subscribe to the transport stream
        responseStream.subscribe(
          response => {
            // If schema validation is enabled, validate each response
            if (features.validateResponseSchema) {
              validator.validate(response, session.getResponseSchema).onComplete {
                case Success(_) => resultStream.next(response)
                case Failure(e) => resultStream.error(new IllegalArgumentException(s"Response validation failed: ${e.getMessage}"))
              }
            } else {
              resultStream.next(response)
            }
          },
          error => resultStream.error(error),
          () => {
            resultStream.complete()
            sessions.remove(sessionId)
            session.getTransportSession.close()
          }
        )

      case Failure(e) =>
        resultStream.error(e)
        sessions.remove(sessionId)
        session.getTransportSession.close()
    }

    resultStream
  }

  /**
   * Closes the client.
   *
   * @return A Future that completes when the client is closed
   */
  override def close(): Future[Unit] = {
    Future {
      if (initialized) {
        sessions.values().asScala.foreach(session =>
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
    val stream = asyncClient.stream(payload)
    stream.subscribe(
      response => callback(response),
      error => throw error,
      () => {}
    )
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
