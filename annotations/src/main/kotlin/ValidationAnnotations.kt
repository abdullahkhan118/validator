package io.github.abdullahkhan118.annotations


annotation class Validate


annotation class ByteRange(val min: Byte = Byte.MIN_VALUE, val max: Byte = Byte.MAX_VALUE)
annotation class ShortRange(val min: Short = Short.MIN_VALUE, val max: Short = Short.MAX_VALUE)
annotation class IntRange(val min: Int = Int.MIN_VALUE, val max: Int = Int.MAX_VALUE)
annotation class LongRange(val min: Long = Long.MIN_VALUE, val max: Long = Long.MAX_VALUE)


annotation class FloatRange(val min: Float = Float.MIN_VALUE, val max: Float = Float.MAX_VALUE)
annotation class DoubleRange(val min: Double = Double.MIN_VALUE, val max: Double = Double.MAX_VALUE)

annotation class Regex(val pattern: String)
