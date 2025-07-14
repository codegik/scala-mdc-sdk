package com.codegik.mdc.server

import com.codegik.mdc.spec.{DefaultJsonSchemaValidator, JsonSchemaValidator, McpServerSession, McpServerTransport, McpServerTransportProvider, McpTransportSession}
import com.codegik.mdc.util.Assert
import com.codegik.mdc.spec._

import java.util.UUID
import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
 * Implementation of McpAsyncServer.
 *
 * @param transportProvider The server transport provider to use
 * @param features Server features configuration
 * @param validator JSON schema validator
 * @param executionContext Execution context for async operations
 */
class McpAsyncServerImpl(
  private val transportProvider: McpServerTransportProvider,
  private val features: McpServerFeatures,
  private val validator: JsonSchemaValidator,
  private implicit val executionContext: ExecutionContext
) extends McpAsyncServer {

  private var transport: McpServerTransport = _
  private val sessions = TrieMap.empty[String, McpServerSession]
  private var initialized = false
  private val exchange = new McpAsyncServerExchangeImpl()

  /**
   * Initializes the server.
   *
   * @return A Future that completes when initialization is done
   */
  override def initialize(): Future[Unit] = {
    Future {
      if (!initialized) {
        // Block on transport creation for simplicity
        import scala.concurrent.Await
        import scala.concurrent.duration._
        transport = Await.result(transportProvider.createTransport(), 30.seconds)
        initialized = true
      }
    }
  }

  /**
   * Gets the server exchange for handling requests and responses asynchronously.
   *
   * @return The async server exchange
   */
  override def getExchange: McpAsyncServerExchange = exchange

  /**
   * Closes the server.
   *
   * @return A Future that completes when the server is closed
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
   * Implementation of McpAsyncServerExchange.
   */
  private class McpAsyncServerExchangeImpl extends McpAsyncServerExchange {
    private var requestHandler: String => Future[String] = _
    private var streamHandler: (String, String => Unit) => Future[Unit] = _

    /**
     * Handles a request asynchronously.
     *
     * @param request The request payload
     * @return A Future with the response
     */
    override def handleRequest(request: String): Future[String] = {
      Assert.hasText(request, "Request cannot be null or empty")
      if (!initialized) {
        return Future.failed(new IllegalStateException("Server not initialized"))
      }

      if (requestHandler == null) {
        return Future.failed(new IllegalStateException("No request handler registered"))
      }

      val sessionId = extractSessionId(request)
      val session = getOrCreateSession(sessionId)

      // If schema validation is enabled, validate the request
      val validationFuture = if (features.validateRequestSchema) {
        validator.validate(request, session.getRequestSchema)
      } else {
        Future.successful(())
      }

      validationFuture.flatMap(_ =>
        requestHandler(request).flatMap(response => {
          // If schema validation is enabled, validate the response
          if (features.validateResponseSchema) {
            validator.validate(response, session.getResponseSchema).map(_ => response)
          } else {
            Future.successful(response)
          }
        })
      )
    }

    /**
     * Handles a streaming request.
     *
     * @param request The request payload
     * @param responseHandler The handler for streaming responses
     * @return A Future that completes when the exchange is done
     */
    override def handleStream(request: String, responseHandler: String => Unit): Future[Unit] = {
      Assert.hasText(request, "Request cannot be null or empty")
      Assert.notNull(responseHandler, "Response handler cannot be null")

      if (!initialized) {
        return Future.failed(new IllegalStateException("Server not initialized"))
      }

      if (streamHandler == null) {
        return Future.failed(new IllegalStateException("No stream handler registered"))
      }

      val sessionId = extractSessionId(request)
      val session = getOrCreateSession(sessionId)

      // If schema validation is enabled, validate the request
      val validationFuture = if (features.validateRequestSchema) {
        validator.validate(request, session.getRequestSchema)
      } else {
        Future.successful(())
      }

      validationFuture.flatMap(_ =>
        streamHandler(request, response => {
          // If schema validation is enabled, validate the response
          if (features.validateResponseSchema) {
            validator.validate(response, session.getResponseSchema).onComplete {
              case Success(_) => responseHandler(response)
              case Failure(e) => throw new IllegalArgumentException(s"Response validation failed: ${e.getMessage}")
            }
          } else {
            responseHandler(response)
          }
        })
      )
    }

    /**
     * Sets the request handler.
     *
     * @param handler The handler for requests
     */
    def setRequestHandler(handler: String => Future[String]): Unit = {
      this.requestHandler = handler
    }

    /**
     * Sets the stream handler.
     *
     * @param handler The handler for streams
     */
    def setStreamHandler(handler: (String, String => Unit) => Future[Unit]): Unit = {
      this.streamHandler = handler
    }
  }

  /**
   * Gets or creates a server session.
   *
   * @param sessionId The session ID
   * @return The server session
   */
  private def getOrCreateSession(sessionId: String): McpServerSession = {
    sessions.getOrElseUpdate(sessionId, {
      val transportSession = transport.getSession(sessionId)
      new DefaultMcpServerSession(transportSession)
    })
  }

  /**
   * Extracts the session ID from a request.
   * This is a simplified implementation - in a real implementation,
   * this would parse JSON and extract the session ID from the payload.
   *
   * @param request The request
   * @return The extracted session ID
   */
  private def extractSessionId(request: String): String = {
    // In a real implementation, this would parse JSON
    // For simplicity, we'll generate a random ID
    UUID.randomUUID().toString
  }
}

/**
 * Implementation of McpSyncServer.
 * This is a wrapper around McpAsyncServer that blocks on operations.
 *
 * @param asyncServer The async server to wrap
 */
class McpSyncServerImpl(private val asyncServer: McpAsyncServer) extends McpSyncServer {
  private val exchange = new McpSyncServerExchangeImpl(asyncServer.getExchange)

  import scala.concurrent.Await
  import scala.concurrent.duration._

  private val DefaultTimeout = 30.seconds

  /**
   * Initializes the server.
   *
   * @return A Future that completes when initialization is done
   */
  override def initialize(): Future[Unit] = {
    asyncServer.initialize()
  }

  /**
   * Gets the server exchange for handling requests and responses synchronously.
   *
   * @return The sync server exchange
   */
  override def getExchange: McpSyncServerExchange = exchange

  /**
   * Closes the server.
   *
   * @return A Future that completes when the server is closed
   */
  override def close(): Future[Unit] = {
    asyncServer.close()
  }

  /**
   * Implementation of McpSyncServerExchange.
   *
   * @param asyncExchange The async exchange to wrap
   */
  private class McpSyncServerExchangeImpl(
    private val asyncExchange: McpAsyncServerExchange
  ) extends McpSyncServerExchange {

    /**
     * Handles a request synchronously.
     *
     * @param request The request payload
     * @return The response
     */
    override def handleRequest(request: String): String = {
      Await.result(asyncExchange.handleRequest(request), DefaultTimeout)
    }

    /**
     * Handles a streaming request.
     *
     * @param request The request payload
     * @param responseHandler The handler for streaming responses
     */
    override def handleStream(request: String, responseHandler: String => Unit): Unit = {
      Await.result(asyncExchange.handleStream(request, responseHandler), DefaultTimeout)
    }
  }
}

/**
 * Default implementation of McpServerSession.
 *
 * @param transportSession The transport session
 */
class DefaultMcpServerSession(private val transportSession: McpTransportSession) extends McpServerSession {
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
 * Factory methods for creating MCP servers.
 */
object McpAsyncServer {
  /**
   * Creates a new async server.
   *
   * @param transportProvider The server transport provider to use
   * @param features Server features configuration
   * @param executionContext Execution context for async operations
   * @return The created server
   */
  def create(transportProvider: McpServerTransportProvider, features: McpServerFeatures = McpServerFeatures.builder().build())
            (implicit executionContext: ExecutionContext = ExecutionContext.global): McpAsyncServer = {
    Assert.notNull(transportProvider, "Transport provider cannot be null")
    Assert.notNull(features, "Features cannot be null")

    val validator = new DefaultJsonSchemaValidator()
    new McpAsyncServerImpl(transportProvider, features, validator, executionContext)
  }
}

/**
 * Factory methods for creating MCP servers.
 */
object McpSyncServer {
  /**
   * Creates a new sync server.
   *
   * @param transportProvider The server transport provider to use
   * @param features Server features configuration
   * @param executionContext Execution context for async operations
   * @return The created server
   */
  def create(transportProvider: McpServerTransportProvider, features: McpServerFeatures = McpServerFeatures.builder().build())
            (implicit executionContext: ExecutionContext = ExecutionContext.global): McpSyncServer = {
    Assert.notNull(transportProvider, "Transport provider cannot be null")
    Assert.notNull(features, "Features cannot be null")

    val asyncServer = McpAsyncServer.create(transportProvider, features)(executionContext)
    new McpSyncServerImpl(asyncServer)
  }
}
