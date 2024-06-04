/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package app.android.issue5101

import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random
import kotlin.random.nextLong

/**
 * Generates and returns a string containing random alphanumeric characters.
 *
 * The characters returned are taken from the set of characters comprising of the 10 numeric digits
 * and the 26 lowercase English characters.
 *
 * @param length the number of random characters to generate and include in the returned string;
 *   must be greater than or equal to zero.
 * @return a string containing the given number of random alphanumeric characters.
 * @hide
 */
fun Random.nextAlphanumericString(length: Int): String {
  require(length >= 0) { "invalid length: $length" }
  return (0 until length).map { ALPHANUMERIC_ALPHABET.random(this) }.joinToString(separator = "")
}

// The set of characters comprising of the 10 numeric digits and the 26 lowercase letters of the
// English alphabet with some characters removed that can look similar in different fonts, like
// '1', 'l', and 'i'.
@Suppress("SpellCheckingInspection")
private const val ALPHANUMERIC_ALPHABET = "23456789abcdefghjkmnpqrstvwxyz"

private val nextSequenceId = AtomicLong(Random.nextLong(1000000000000..9999999999999))

/**
 * Returns a positive number on each invocation, with each returned value being strictly greater
 * than any value previously returned in this process.
 *
 * This function is thread-safe and may be called concurrently by multiple threads and/or
 * coroutines.
 */
internal fun nextSequenceNumber(): Long {
  return nextSequenceId.incrementAndGet()
}

internal class NullableReference<T>(val ref: T? = null) {
  override fun equals(other: Any?) = (other is NullableReference<*>) && other.ref == ref

  override fun hashCode() = ref?.hashCode() ?: 0

  override fun toString() = ref?.toString() ?: "null"
}
