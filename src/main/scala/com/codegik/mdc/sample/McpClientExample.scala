package com.codegik.mdc.sample

import com.codegik.mdc.client.{McpAsyncClient, McpClientFeatures}
import com.codegik.mdc.client.transport.StdioClientTransport

import scala.concurrent.{Await, Future, Promise}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

/**
 * Example of using the MCP SDK to communicate with an AI model.
 * This example demonstrates both request/response and streaming patterns.
 */
object McpClientExample {

  def main(args: Array[String]): Unit = {
    println("Starting MCP Client Example...")

    // Create a client with standard I/O transport
    val transport = new StdioClientTransport()

    // Configure client features
    val features = McpClientFeatures.builder()
      .validateRequestSchema(false) // Disable schema validation for simplicity
      .validateResponseSchema(false)
      .build()

    // Create an async client
    val client = McpAsyncClient.create(transport, features)

    try {
      // Initialize the client
      println("Initializing client...")
      Await.result(client.initialize(), 30.seconds)
      println("Client initialized successfully")

      // Example 1: Simple request/response
      val response = simpleRequest(client)
      println(s"Received response: $response")

      // Example 2: Streaming response
      streamingRequest(client)

    } catch {
      case e: Exception =>
        println(s"Error: ${e.getMessage}")
        e.printStackTrace()
    } finally {
      // Clean up resources
      Await.result(client.close(), 30.seconds)
      println("Client closed successfully")
    }
  }

  /**
   * Demonstrates a simple request/response pattern.
   */
  def simpleRequest(client: McpAsyncClient): String = {
    println("Sending a simple request...")

    val request = """
      {
        "messages": [
          {
            "role": "user",
            "content": "What is the capital of France?"
          }
        ],
        "model": "gpt-3.5-turbo"
      }
    """

    // Send the request and wait for response
    Await.result(client.request(request), 30.seconds)
  }

  /**
   * Demonstrates streaming responses.
   */
  def streamingRequest(client: McpAsyncClient): Unit = {
    println("Sending a streaming request...")

    val request = """
      {
        "messages": [
          {
            "role": "user",
            "content": "Write a short poem about artificial intelligence."
          }
        ],
        "model": "gpt-3.5-turbo",
        "stream": true
      }
    """

    // For tracking when the stream completes
    val streamCompleted = Promise[Unit]()

    // Start streaming
    val stream = client.stream(request)

    // Process the stream elements in a separate Future
    Future {
      try {
        // If the stream is empty, it might mean there was an error
        if (stream.isEmpty) {
          println("Stream was empty or failed to initialize")
          streamCompleted.trySuccess(())
        } else {
          // Process each element as it becomes available
          stream.foreach { response =>
            println(s"Received chunk: $response")
          }

          // After processing all elements, mark as complete
          println("Stream processing finished")
          streamCompleted.trySuccess(())
        }
      } catch {
        case e: Exception =>
          println(s"Stream processing error: ${e.getMessage}")
          streamCompleted.tryFailure(e)
      }
    }

    // Wait for the stream to complete
    println("Waiting for stream to complete...")
    Await.result(streamCompleted.future, 60.seconds)
  }
}
