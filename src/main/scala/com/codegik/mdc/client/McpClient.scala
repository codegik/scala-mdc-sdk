package com.codegik.mdc.client

import scala.concurrent.Future

/**
 * Base interface for MCP clients.
 */
trait McpClient {
  /**
   * Initializes the client.
   *
   * @return A Future that completes when initialization is done
   */
  def initialize(): Future[Unit]

  /**
   * Closes the client.
   *
   * @return A Future that completes when the client is closed
   */
  def close(): Future[Unit]
}

/**
 * Interface for asynchronous MCP clients.
 */
trait McpAsyncClient extends McpClient {
  /**
   * Sends a request asynchronously.
   *
   * @param payload The request payload
   * @return A Future with the response
   */
  def request(payload: String): Future[String]

  /**
   * Establishes a streaming connection.
   *
   * @param payload The request payload
   * @return A stream of responses
   */
  def stream(payload: String): LazyList[String]
}

/**
 * Interface for synchronous MCP clients.
 */
trait McpSyncClient extends McpClient {
  /**
   * Sends a request synchronously.
   *
   * @param payload The request payload
   * @return The response
   */
  def request(payload: String): String

  /**
   * Establishes a streaming connection and processes the stream with a callback.
   *
   * @param payload The request payload
   * @param callback The callback to process streaming responses
   */
  def stream(payload: String, callback: String => Unit): Unit
}
