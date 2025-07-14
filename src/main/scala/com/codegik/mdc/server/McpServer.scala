package com.codegik.mdc.server

import scala.concurrent.Future

/**
 * Base interface for MCP servers.
 */
trait McpServer {
  /**
   * Initializes the server.
   *
   * @return A Future that completes when initialization is done
   */
  def initialize(): Future[Unit]

  /**
   * Closes the server.
   *
   * @return A Future that completes when the server is closed
   */
  def close(): Future[Unit]
}

/**
 * Interface for asynchronous MCP servers.
 */
trait McpAsyncServer extends McpServer {
  /**
   * Gets the server exchange for handling requests and responses asynchronously.
   *
   * @return The async server exchange
   */
  def getExchange: McpAsyncServerExchange
}

/**
 * Interface for synchronous MCP servers.
 */
trait McpSyncServer extends McpServer {
  /**
   * Gets the server exchange for handling requests and responses synchronously.
   *
   * @return The sync server exchange
   */
  def getExchange: McpSyncServerExchange
}

/**
 * Interface for asynchronous server exchanges.
 */
trait McpAsyncServerExchange {
  /**
   * Handles a request asynchronously.
   *
   * @param request The request payload
   * @return A Future with the response
   */
  def handleRequest(request: String): Future[String]

  /**
   * Handles a streaming request.
   *
   * @param request The request payload
   * @param responseHandler The handler for streaming responses
   * @return A Future that completes when the exchange is done
   */
  def handleStream(request: String, responseHandler: String => Unit): Future[Unit]
}

/**
 * Interface for synchronous server exchanges.
 */
trait McpSyncServerExchange {
  /**
   * Handles a request synchronously.
   *
   * @param request The request payload
   * @return The response
   */
  def handleRequest(request: String): String

  /**
   * Handles a streaming request.
   *
   * @param request The request payload
   * @param responseHandler The handler for streaming responses
   */
  def handleStream(request: String, responseHandler: String => Unit): Unit
}
