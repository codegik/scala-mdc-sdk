package com.codegik.mdc.spec

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.networknt.schema.{JsonSchema, JsonSchemaFactory, SpecVersion}

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

/**
 * Default implementation of the JsonSchemaValidator interface using Scala Futures.
 */
class DefaultJsonSchemaValidator(implicit val executionContext: ExecutionContext) extends JsonSchemaValidator {
  private val objectMapper = new ObjectMapper()
  private val schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7)

  /**
   * Validates the provided JSON against the schema.
   *
   * @param json The JSON to validate
   * @param schema The schema to validate against
   * @return A Future that completes successfully if validation passes, or with an error if validation fails
   */
  override def validate(json: String, schema: String): Future[Unit] = {
    Future {
      Try {
        val jsonNode: JsonNode = objectMapper.readTree(json)
        val jsonSchema: JsonSchema = schemaFactory.getSchema(schema)
        val errors = jsonSchema.validate(jsonNode)

        if (!errors.isEmpty) {
          val errorMessages = errors.asScala.map(_.getMessage).mkString(", ")
          throw new IllegalArgumentException(s"JSON validation failed: $errorMessages")
        }
      } match {
        case Success(_) => ()
        case Failure(exception) => throw exception
      }
    }
  }
}
