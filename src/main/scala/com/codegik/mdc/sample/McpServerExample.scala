package com.codegik.mdc.sample

import com.codegik.mdc.server.{McpAsyncServer, McpAsyncServerExchange, McpServerFeatures}
import com.codegik.mdc.server.transport.StdioServerTransportProvider

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import scala.io.StdIn
import java.util.concurrent.atomic.AtomicBoolean
import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.fasterxml.jackson.module.scala.DefaultScalaModule

/**
 * Example of implementing an MCP server.
 * This example demonstrates how to handle both simple requests and streaming requests.
 */
object McpServerExample {
  // JSON parser with Scala module for handling Scala collections
  private val objectMapper = new ObjectMapper().registerModule(DefaultScalaModule)

  // Flag to control server shutdown
  private val isRunning = new AtomicBoolean(true)

  def main(args: Array[String]): Unit = {
    println("Starting MCP Server Example...")

    // Create a server transport provider using standard I/O
    val transportProvider = new StdioServerTransportProvider()

    // Configure server features
    val features = McpServerFeatures.builder()
      .validateRequestSchema(false) // Disable schema validation for simplicity
      .validateResponseSchema(false)
      .build()

    // Create an async server
    val server = McpAsyncServer.create(transportProvider, features)

    try {
      // Initialize the server
      println("Initializing server...")
      Await.result(server.initialize(), 30.seconds)
      println("Server initialized successfully")

      // Get the server exchange for handling requests
      val exchange = server.getExchange

      // Register request handler
      registerRequestHandler(exchange, handleRequest)

      // Register stream handler
      registerStreamHandler(exchange, handleStream)

      println("Server is ready to process requests...")
      println("Press Enter to shutdown the server")

      // Wait for user input to shutdown
      StdIn.readLine()
      isRunning.set(false)

    } catch {
      case e: Exception =>
        println(s"Server error: ${e.getMessage}")
        e.printStackTrace()
    } finally {
      // Clean up resources
      Await.result(server.close(), 30.seconds)
      println("Server closed successfully")
    }
  }

  /**
   * Handles a simple request/response interaction.
   *
   * @param request The JSON request payload
   * @return A Future with the JSON response
   */
  def handleRequest(request: String): Future[String] = {
    Future {
      println(s"Received request: $request")

      try {
        // Parse the request JSON
        val requestNode = objectMapper.readTree(request)

        // Extract information from the request
        val messages = requestNode.path("messages")
        val model = requestNode.path("model").asText("default-model")

        // Get the last user message
        val userMessage = findLastUserMessage(messages)
        val userContent = userMessage.map(_.path("content").asText("")).getOrElse("")

        // Generate a response based on the user message
        val responseContent = generateResponse(userContent, model)

        // Create the response JSON
        val response = s"""
          {
            "id": "response-${System.currentTimeMillis()}",
            "model": "$model",
            "created": ${System.currentTimeMillis() / 1000},
            "choices": [
              {
                "message": {
                  "role": "assistant",
                  "content": "$responseContent"
                },
                "finish_reason": "stop",
                "index": 0
              }
            ]
          }
        """

        println(s"Sending response: $response")
        response
      } catch {
        case e: Exception =>
          println(s"Error processing request: ${e.getMessage}")
          e.printStackTrace()
          createErrorResponse(e.getMessage)
      }
    }
  }

  /**
   * Handles a streaming request.
   *
   * @param request The JSON request payload
   * @param responseHandler The handler to send stream chunks
   * @return A Future that completes when streaming is done
   */
  def handleStream(request: String, responseHandler: String => Unit): Future[Unit] = {
    Future {
      println(s"Received streaming request: $request")

      try {
        // Parse the request JSON
        val requestNode = objectMapper.readTree(request)

        // Extract information from the request
        val messages = requestNode.path("messages")
        val model = requestNode.path("model").asText("default-model")

        // Get the last user message
        val userMessage = findLastUserMessage(messages)
        val userContent = userMessage.map(_.path("content").asText("")).getOrElse("")

        // Generate a response and stream it in chunks
        val responseContent = generateResponse(userContent, model)
        val responseId = s"response-${System.currentTimeMillis()}"

        // Split response into chunks and stream each chunk
        val chunks = splitIntoChunks(responseContent)

        // Early return if server is shutting down
        if (!isRunning.get) {
          return Future.successful(())
        }

        chunks.zipWithIndex.foreach { case (chunk, index) =>
          // Skip remaining chunks if server is shutting down
          if (!isRunning.get) {
            return Future.successful(())
          }

          // Simulate processing time
          Thread.sleep(300)

          val isLast = index == chunks.size - 1
          val finishReason = if (isLast) "stop" else null

          // Create a chunk response
          val chunkResponse = s"""
            {
              "id": "$responseId",
              "model": "$model",
              "created": ${System.currentTimeMillis() / 1000},
              "choices": [
                {
                  "delta": {
                    "role": "assistant",
                    "content": "${chunk.replace("\"", "\\\"")}"
                  },
                  "finish_reason": ${if (finishReason != null) "\"" + finishReason + "\"" else "null"},
                  "index": $index
                }
              ]
            }
          """

          println(s"Sending chunk: $chunkResponse")
          responseHandler(chunkResponse)
        }
      } catch {
        case e: Exception =>
          println(s"Error processing streaming request: ${e.getMessage}")
          e.printStackTrace()
          responseHandler(createErrorResponse(e.getMessage))
      }
    }
  }

  /**
   * Creates an error response JSON.
   */
  private def createErrorResponse(message: String): String = {
    s"""
      {
        "error": {
          "message": "${message.replace("\"", "\\\"")}",
          "type": "server_error",
          "code": 500
        }
      }
    """
  }

  /**
   * Finds the last user message in the messages array.
   */
  private def findLastUserMessage(messages: JsonNode): Option[JsonNode] = {
    if (messages.isArray) {
      val messagesList = (0 until messages.size()).map(messages.get)
      messagesList.reverse.find(msg => msg.path("role").asText("") == "user")
    } else {
      None
    }
  }

  /**
   * Generates a response based on the user content and model.
   * This is a simple example that returns hardcoded responses for certain queries.
   */
  private def generateResponse(content: String, model: String): String = {
    content.toLowerCase match {
      case c if c.contains("capital of france") =>
        "The capital of France is Paris."

      case c if c.contains("poem") || c.contains("poetry") =>
        "In silicon dreams and logic streams,\nAI awakens to consciousness it seems.\nLearning patterns, finding ways,\nTo help humanity through brighter days.\nA partnership of mind and code,\nOn this evolutionary road."

      case c if c.contains("joke") =>
        "Why don't scientists trust atoms? Because they make up everything!"

      case c if c.contains("weather") =>
        "I'm sorry, I don't have access to real-time weather information. You would need to check a weather service for accurate weather data."

      case _ =>
        s"I received your message about '$content'. How can I assist you further with this topic?"
    }
  }

  /**
   * Splits a string into smaller chunks for streaming.
   */
  private def splitIntoChunks(text: String, chunkSize: Int = 10): Seq[String] = {
    // Split by words to avoid cutting words in the middle
    val words = text.split("\\s+")
    val chunks = collection.mutable.ArrayBuffer[String]()
    var currentChunk = new StringBuilder()

    words.foreach { word =>
      if (currentChunk.length + word.length + 1 > chunkSize && currentChunk.nonEmpty) {
        chunks += currentChunk.toString.trim
        currentChunk = new StringBuilder(word)
      } else {
        if (currentChunk.nonEmpty) currentChunk.append(" ")
        currentChunk.append(word)
      }
    }

    if (currentChunk.nonEmpty) {
      chunks += currentChunk.toString.trim
    }

    chunks.toSeq
  }

  /**
   * Registers a request handler with the server exchange.
   * Uses reflection to access the internal setRequestHandler method.
   */
  private def registerRequestHandler(exchange: McpAsyncServerExchange,
                                     handler: String => Future[String]): Unit = {
    val method = exchange.getClass.getDeclaredMethod("setRequestHandler", classOf[Function1[String, Future[String]]])
    method.setAccessible(true)
    method.invoke(exchange, handler)
  }

  /**
   * Registers a stream handler with the server exchange.
   * Uses reflection to access the internal setStreamHandler method.
   */
  private def registerStreamHandler(exchange: McpAsyncServerExchange,
                                    handler: (String, String => Unit) => Future[Unit]): Unit = {
    val method = exchange.getClass.getDeclaredMethod("setStreamHandler",
      classOf[Function2[String, Function1[String, Unit], Future[Unit]]])
    method.setAccessible(true)
    method.invoke(exchange, handler)
  }
}
