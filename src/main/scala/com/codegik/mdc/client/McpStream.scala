//package com.codegik.mdc.client
//
///**
// * Type alias for the streaming response.
// * Using Scala's built-in LazyList instead of a custom implementation.
// *
// * LazyList is a sequence that computes its elements only when they are needed.
// * It can represent infinite sequences and is memory efficient.
// */
//type McpStream[T] = LazyList[T]
//
///**
// * Utility object with factory methods for creating and working with McpStream (LazyList).
// */
//object McpStream {
//  /**
//   * Creates an empty stream.
//   *
//   * @return An empty LazyList
//   */
//  def empty[T]: LazyList[T] = LazyList.empty[T]
//
//  /**
//   * Creates a stream with a single element.
//   *
//   * @param value The element to include in the stream
//   * @return A LazyList with a single element
//   */
//  def single[T](value: T): LazyList[T] = LazyList(value)
//
//  /**
//   * Creates a stream from a sequence of elements.
//   *
//   * @param values The elements to include in the stream
//   * @return A LazyList containing the elements
//   */
//  def from[T](values: T*): LazyList[T] = LazyList.from(values)
//}
