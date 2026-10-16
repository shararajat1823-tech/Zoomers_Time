package com.zoomers.app.core.network

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Retrofit
import java.lang.reflect.Type

/**
 * A small Retrofit [Converter.Factory] backed by kotlinx.serialization.
 *
 * We deliberately do NOT use the third-party
 * `com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter`
 * artifact: its packaging resolves inconsistently on the Android compile
 * classpath (the `retrofit2.converter.kotlinx` package goes unresolved across
 * multiple published versions). This factory depends only on retrofit-core and
 * kotlinx-serialization-json, both of which resolve cleanly, so the JSON wiring
 * stays reliable. It serializes request bodies and deserializes response bodies
 * using a reflectively-resolved serializer for the target [Type].
 */
class KotlinxJsonConverterFactory private constructor(
    private val json: Json,
    private val contentType: MediaType,
) : Converter.Factory() {

    @OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
    override fun responseBodyConverter(
        type: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit,
    ): Converter<ResponseBody, *> {
        val loader = json.serializersModule.serializer(type)
        return Converter<ResponseBody, Any?> { body ->
            body.use { json.decodeFromString(loader, it.string()) }
        }
    }

    @OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
    override fun requestBodyConverter(
        type: Type,
        parameterAnnotations: Array<out Annotation>,
        methodAnnotations: Array<out Annotation>,
        retrofit: Retrofit,
    ): Converter<*, RequestBody> {
        val saver = json.serializersModule.serializer(type)
        return Converter<Any?, RequestBody> { value ->
            json.encodeToString(saver, value).toRequestBody(contentType)
        }
    }

    companion object {
        fun create(
            json: Json,
            contentType: MediaType = "application/json".toMediaType(),
        ): KotlinxJsonConverterFactory = KotlinxJsonConverterFactory(json, contentType)
    }
}
