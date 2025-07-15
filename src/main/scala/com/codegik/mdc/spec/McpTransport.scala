package com.codegik.mdc.spec

import scala.concurrent.Future
import scala.concurrent.duration.Duration

/**
 * Base transport interface for the Model Context Protocol.
 */
trait McpTransport {
  /**
   * Gets the unique identifier for this transport.
   *
   * @return The transport identifier
   */
  def getId: String
}

/**
 * Transport interface for MCP clients to communicate with servers.
 */
trait McpClientTransport extends McpTransport {
  /**
   * Initializes the client transport.
   *
   * @return A Future that completes when initialization is done
   */
  def initialize(): Future[Unit]

  /**
   * Sends a request to the server.
   *
   * @param session The transport session
   * @param payload The request payload
   * @return A Future with the response
   */
  def request(session: McpTransportSession, payload: String): Future[String]

  /**
   * Establishes a streaming connection with the server.
   *
   * @param session The transport session
   * @param payload The request payload
   * @return A stream of responses
   */
  def stream(session: McpTransportSession, payload: String): LazyList[String]

  /**
   * Closes the transport.
   *
   * @return A Future that completes when the transport is closed
   */
  def close(): Future[Unit]
}

/**
 * Transport interface for MCP servers to communicate with clients.
 */
trait McpServerTransport extends McpTransport {
  /**
   * Gets the transport session for the given session ID.
   *
   * @param sessionId The session ID
   * @return The transport session, or throws an exception if not found
   */
  def getSession(sessionId: String): McpTransportSession

  /**
   * Closes the transport.
   *
   * @return A Future that completes when the transport is closed
   */
  def close(): Future[Unit]
}

/**
 * Provider interface for creating server transports.
 */
trait McpServerTransportProvider {
  /**
   * Creates a new server transport.
   *
   * @return A Future with the created transport
   */
  def createTransport(): Future[McpServerTransport]
}
