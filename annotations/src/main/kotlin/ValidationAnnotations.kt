package io.github.abdullahkhan118.annotations


annotation class Validate

annotation class ByteRange(val min: Byte = Byte.MIN_VALUE, val max: Byte = Byte.MAX_VALUE)
annotation class ShortRange(val min: Short = Short.MIN_VALUE, val max: Short = Short.MAX_VALUE)
annotation class IntRange(val min: Int = Int.MIN_VALUE, val max: Int = Int.MAX_VALUE)
annotation class LongRange(val min: Long = Long.MIN_VALUE, val max: Long = Long.MAX_VALUE)


annotation class FloatRange(val min: Float = Float.MIN_VALUE, val max: Float = Float.MAX_VALUE)
annotation class DoubleRange(val min: Double = Double.MIN_VALUE, val max: Double = Double.MAX_VALUE)

annotation class ByteDistinct(vararg val values: Byte)
annotation class ShortDistinct(vararg val values: Short)
annotation class IntDistinct(vararg val values: Int)
annotation class LongDistinct(vararg val values: Long)


annotation class FloatDistinct(vararg val values: Float)
annotation class DoubleDistinct(vararg val values: Double)

annotation class Regex(val pattern: String)

annotation class StringDistinct(vararg val values: String)
