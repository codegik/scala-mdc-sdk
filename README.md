# Model Context Protocol (MCP) Scala SDK

A Scala implementation of the [Model Context Protocol](https://github.com/modelcontextprotocol/modelcontextprotocol), enabling seamless integration with language models and AI tools.

## Overview

The Model Context Protocol (MCP) Scala SDK provides a standardized way for Scala applications to interact with language models and AI tools, regardless of the underlying model provider. This SDK implements the MCP specification, allowing developers to build applications that can work with various AI models through a consistent interface.

## Features

- **Transport Agnostic**: Supports multiple transport mechanisms (currently: standard I/O, with HTTP support planned)
- **Synchronous and Asynchronous APIs**: Choose between blocking and non-blocking APIs based on your needs
- **Schema Validation**: Built-in JSON schema validation for requests and responses
- **Reactive Programming**: Built on Reactor Core for robust reactive programming capabilities

## Installation

Add the following dependency to your Maven project:

```xml
<dependency>
    <groupId>io.modelcontextprotocol.sdk</groupId>
    <artifactId>scala-sdk</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

For SBT:

```scala
libraryDependencies += "io.modelcontextprotocol.sdk" % "scala-sdk" % "0.1.0-SNAPSHOT"
```

## Quick Start

### Client Example

```scala
import io.modelcontextprotocol.client.McpAsyncClient
import io.modelcontextprotocol.client.McpClientFeatures
import io.modelcontextprotocol.client.transport.StdioClientTransport
import scala.concurrent.Await
import scala.concurrent.duration._

// Create a client with standard I/O transport
val transport = new StdioClientTransport()
val features = McpClientFeatures.builder().build()

val client = McpAsyncClient.create(transport, features)
Await.result(client.initialize(), 30.seconds)

// Send a request
val response = Await.result(client.request("""{"query": "What is the capital of France?"}"""), 30.seconds)
println(s"Response: $response")

// Cleanup
Await.result(client.close(), 30.seconds)
```

### Server Example

```scala
import io.modelcontextprotocol.server.McpAsyncServer
import io.modelcontextprotocol.server.McpServerFeatures
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

// Create a server with standard I/O transport
val transportProvider = new StdioServerTransportProvider()
val features = McpServerFeatures.builder().build()

val server = McpAsyncServer.create(transportProvider, features)
Await.result(server.initialize(), 30.seconds)

// Set up request handling
val exchange = server.getExchange
registerRequestHandler(exchange, request => {
  // Process the request and return a response
  Future.successful(s"""{"response": "This is a response to: $request"}""")
})

// Wait for shutdown signal...
println("Press Enter to shutdown the server")
scala.io.StdIn.readLine()

// Cleanup
Await.result(server.close(), 30.seconds)

// Helper method to register request handler using reflection
def registerRequestHandler(exchange: McpAsyncServerExchange, handler: String => Future[String]): Unit = {
  val method = exchange.getClass.getDeclaredMethod("setRequestHandler", classOf[Function1[String, Future[String]]])
  method.setAccessible(true)
  method.invoke(exchange, handler)
}
```

## Documentation

For more detailed information about the Model Context Protocol, please visit the [official MCP repository](https://github.com/modelcontextprotocol/modelcontextprotocol).

## License

This project is licensed under the terms of the Apache License 2.0.
