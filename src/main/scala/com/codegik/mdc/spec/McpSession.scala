package com.codegik.mdc.spec

import scala.concurrent.Future

/**
 * Base interface for MCP transport sessions.
 */
trait McpTransportSession {
  /**
   * Gets the unique session ID.
   *
   * @return The session ID
   */
  def getId: String

  /**
   * Sends a message to the other endpoint.
   *
   * @param payload The message payload
   * @return A Future that completes when the message is sent
   */
  def send(payload: String): Future[Unit]

  /**
   * Closes the session.
   *
   * @return A Future that completes when the session is closed
   */
  def close(): Future[Unit]

  /**
   * Gets the stream of incoming messages.
   *
   * @return A stream of incoming messages
   */
  def getIncomingMessages: LazyList[String]
}

/**
 * Thrown when a transport session is not found.
 */
class McpTransportSessionNotFoundException(sessionId: String)
  extends RuntimeException(s"Transport session not found: $sessionId")

/**
 * Interface for client sessions.
 */
trait McpClientSession {
  /**
   * Gets the transport session.
   *
   * @return The transport session
   */
  def getTransportSession: McpTransportSession

  /**
   * Gets the request schema.
   *
   * @return The request schema
   */
  def getRequestSchema: String

  /**
   * Gets the response schema.
   *
   * @return The response schema
   */
  def getResponseSchema: String
}

/**
 * Interface for server sessions.
 */
trait McpServerSession {
  /**
   * Gets the transport session.
   *
   * @return The transport session
   */
  def getTransportSession: McpTransportSession

  /**
   * Gets the request schema.
   *
   * @return The request schema
   */
  def getRequestSchema: String

  /**
   * Gets the response schema.
   *
   * @return The response schema
   */
  def getResponseSchema: String
}

/**
 * Base interface for MCP sessions.
 */
trait McpSession
