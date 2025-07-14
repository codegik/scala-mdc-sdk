package com.codegik.mdc.spec

/**
 * Represents an error in the Model Context Protocol.
 *
 * @param code The error code
 * @param message The error message
 * @param details Optional additional details about the error
 */
case class McpError(
  code: String,
  message: String,
  details: Option[Map[String, Any]] = None
)
