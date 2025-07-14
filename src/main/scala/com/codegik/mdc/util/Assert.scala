package com.codegik.mdc.util

/**
 * Utility class for assertions.
 */
object Assert {
  /**
   * Asserts that the given object is not null.
   *
   * @param obj The object to check
   * @param message The error message
   * @throws IllegalArgumentException if the object is null
   */
  def notNull(obj: Any, message: String): Unit = {
    if (obj == null) {
      throw new IllegalArgumentException(message)
    }
  }

  /**
   * Asserts that the given string is not null or empty.
   *
   * @param str The string to check
   * @param message The error message
   * @throws IllegalArgumentException if the string is null or empty
   */
  def hasText(str: String, message: String): Unit = {
    if (str == null || str.trim.isEmpty) {
      throw new IllegalArgumentException(message)
    }
  }

  /**
   * Asserts that the given condition is true.
   *
   * @param condition The condition to check
   * @param message The error message
   * @throws IllegalArgumentException if the condition is false
   */
  def isTrue(condition: Boolean, message: String): Unit = {
    if (!condition) {
      throw new IllegalArgumentException(message)
    }
  }
}
