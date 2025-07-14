package com.codegik.mdc.server.transport

import com.codegik.mdc.client.McpStreamImpl
import com.codegik.mdc.spec.{DefaultMcpTransportSession, McpServerTransport, McpServerTransportProvider, McpTransportSession, McpTransportSessionNotFoundException}

import java.io.{BufferedReader, BufferedWriter, InputStreamReader, OutputStreamWriter}
import java.util.UUID
import java.util.concurrent.{ConcurrentHashMap, Executors}
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future, Promise}

/**
 * Implementation of McpServerTransport using standard I/O.
 */
class StdioServerTransport(implicit val executionContext: ExecutionContext) extends McpServerTransport {
  private val id = UUID.randomUUID().toString
  private val sessions = new ConcurrentHashMap[String, McpTransportSession]()
  private var initialized = false
  private var reader: BufferedReader = _
  private var writer: BufferedWriter = _
  private val executor = Executors.newSingleThreadExecutor()

  /**
   * Gets the unique identifier for this transport.
   *
   * @return The transport identifier
   */
  override def getId: String = id

  /**
   * Gets the transport session for the given session ID.
   *
   * @param sessionId The session ID
   * @return The transport session, or throws an exception if not found
   */
  override def getSession(sessionId: String): McpTransportSession = {
    val session = sessions.get(sessionId)
    if (session == null) {
      throw new McpTransportSessionNotFoundException(sessionId)
    }
    session
  }

  /**
   * Initializes the server transport.
   *
   * @return A Future that completes when initialization is done
   */
  def initialize(): Future[Unit] = {
    Future {
      if (!initialized) {
        reader = new BufferedReader(new InputStreamReader(System.in))
        writer = new BufferedWriter(new OutputStreamWriter(System.out))
        initialized = true

        // Start reading from stdin in a separate thread
        executor.submit(new Runnable {
          override def run(): Unit = {
            try {
              var line: String = null
              while ({line = reader.readLine(); line != null}) {
                val sessionId = extractSessionId(line)
                var session = sessions.get(sessionId)

                if (session == null) {
                  // Create a new session if it doesn't exist
                  val newSession = new DefaultMcpTransportSession(sessionId)
                  sessions.putIfAbsent(sessionId, newSession)
                  session = newSession

                  // Set up message handling for the new session
                  session.getIncomingMessages.subscribe(
                    response => {
                      try {
                        writer.write(response)
                        writer.newLine()
                        writer.flush()
                      } catch {
                        case e: Exception => e.printStackTrace()
                      }
                    },
                    error => error.printStackTrace(),
                    () => {}
                  )
                }

                session.send(line)
              }
            } catch {
              case e: Exception =>
                e.printStackTrace()
            }
          }
        })
      }
    }
  }

  /**
   * Closes the transport.
   *
   * @return A Future that completes when the transport is closed
   */
  override def close(): Future[Unit] = {
    Future {
      if (initialized) {
        executor.shutdown()
        sessions.values().asScala.foreach(_.close())
        sessions.clear()
        reader.close()
        writer.close()
        initialized = false
      }
    }
  }

  /**
   * Extracts the session ID from a message.
   * This is a simplified implementation - in a real implementation,
   * this would parse JSON and extract the session ID from the payload.
   *
   * @param message The message
   * @return The extracted session ID
   */
  private def extractSessionId(message: String): String = {
    // In a real implementation, this would parse JSON
    // For simplicity, we'll generate a random ID if none exists
    UUID.randomUUID().toString
  }
}

/**
 * Provider for creating StdioServerTransport instances.
 */
class StdioServerTransportProvider(implicit val executionContext: ExecutionContext) extends McpServerTransportProvider {
  /**
   * Creates a new server transport.
   *
   * @return A Future with the created transport
   */
  override def createTransport(): Future[McpServerTransport] = {
    Future {
      val transport = new StdioServerTransport()
      transport.initialize()
      transport
    }
  }
}
