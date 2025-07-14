package com.codegik.mdc.server

import com.codegik.mdc.spec.McpSchema

/**
 * Configuration class for MCP server features.
 *
 * @param protocolVersion The protocol version to use
 * @param validateRequestSchema Whether to validate request schemas
 * @param validateResponseSchema Whether to validate response schemas
 */
case class McpServerFeatures(
  protocolVersion: String,
  validateRequestSchema: Boolean,
  validateResponseSchema: Boolean
)

/**
 * Builder for McpServerFeatures.
 */
object McpServerFeatures {
  /**
   * Creates a new builder for McpServerFeatures.
   *
   * @return A new builder
   */
  def builder(): Builder = new Builder()

  /**
   * Builder class for McpServerFeatures.
   */
  class Builder {
    private var protocolVersion: String = McpSchema.CURRENT_VERSION
    private var validateRequestSchema: Boolean = true
    private var validateResponseSchema: Boolean = true

    /**
     * Sets the protocol version.
     *
     * @param version The protocol version
     * @return This builder
     */
    def protocolVersion(version: String): Builder = {
      this.protocolVersion = version
      this
    }

    /**
     * Sets whether to validate request schemas.
     *
     * @param validate Whether to validate
     * @return This builder
     */
    def validateRequestSchema(validate: Boolean): Builder = {
      this.validateRequestSchema = validate
      this
    }

    /**
     * Sets whether to validate response schemas.
     *
     * @param validate Whether to validate
     * @return This builder
     */
    def validateResponseSchema(validate: Boolean): Builder = {
      this.validateResponseSchema = validate
      this
    }

    /**
     * Builds the McpServerFeatures.
     *
     * @return The built McpServerFeatures
     */
    def build(): McpServerFeatures = {
      McpServerFeatures(
        protocolVersion = protocolVersion,
        validateRequestSchema = validateRequestSchema,
        validateResponseSchema = validateResponseSchema
      )
    }
  }
}
