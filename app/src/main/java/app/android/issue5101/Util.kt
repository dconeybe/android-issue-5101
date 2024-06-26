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

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

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

fun readAllBytes(inputStream: InputStream): ByteArray {
  val buffer = ByteArray(8192) // 8 KB buffer for efficient reading
  val outputStream = ByteArrayOutputStream()

  var bytesRead: Int
  while (inputStream.read(buffer).also { bytesRead = it } > 0) {
    outputStream.write(buffer, 0, bytesRead)
  }

  return outputStream.toByteArray()
}

private val nextSequenceId = AtomicLong(0)

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

internal data class SequencedReference<out T>(val sequenceNumber: Long, val ref: T)

internal fun <T, U> SequencedReference<T>.map(block: (T) -> U): SequencedReference<U> =
    SequencedReference(sequenceNumber, block(ref))

internal suspend fun <T, U> SequencedReference<T>.mapSuspending(
    block: suspend (T) -> U
): SequencedReference<U> = SequencedReference(sequenceNumber, block(ref))

internal fun <T, U : SequencedReference<T>?> U.newerOfThisAnd(other: U): U =
    if (this == null && other == null) {
      // Suppress the warning that `this` is guaranteed to be null because the `null` literal cannot
      // be used in place of `this` because if this extension function is called on a non-nullable
      // reference then `null` is a forbidden return value and compilation will fail.
      @Suppress("KotlinConstantConditions") this
    } else if (this == null) {
      other
    } else if (other == null) {
      this
    } else if (this.sequenceNumber > other.sequenceNumber) {
      this
    } else {
      other
    }

internal inline fun <T : Any, reified U : T> SequencedReference<T>.asTypeOrNull():
    SequencedReference<U>? =
    if (ref is U) {
      @Suppress("UNCHECKED_CAST")
      this as SequencedReference<U>
    } else {
      null
    }

internal inline fun <T : Any, reified U : T> SequencedReference<T>.asTypeOrThrow():
    SequencedReference<U> =
    asTypeOrNull()
        ?: throw IllegalStateException(
            "expected ref to have type ${U::class.qualifiedName}, " +
                "but got ${ref::class.qualifiedName} ($ref)")
