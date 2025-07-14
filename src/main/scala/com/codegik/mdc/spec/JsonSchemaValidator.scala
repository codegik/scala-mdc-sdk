package com.codegik.mdc.spec

import scala.concurrent.Future

/**
 * Defines the JSON Schema validation interface for the Model Context Protocol.
 */
trait JsonSchemaValidator {
  /**
   * Validates the provided JSON against the schema.
   *
   * @param json The JSON to validate
   * @param schema The schema to validate against
   * @return A Future that completes successfully if validation passes, or with an error if validation fails
   */
  def validate(json: String, schema: String): Future[Unit]
}
