package com.yoox.net

import com.yoox.net.mapping.toOutboundItem
import com.yoox.net.mapping.toOutboundSearchResults
import com.yoox.net.models.outbound.DepartmentType
import com.yoox.net.models.outbound.Filter
import com.yoox.net.models.outbound.Item
import com.yoox.net.models.outbound.PriceFilter
import com.yoox.net.models.outbound.SearchResults
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttpConfig
import io.ktor.client.engine.okhttp.OkHttpEngine
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.http.URLBuilder
import io.ktor.http.clone
import kotlinx.serialization.KSerializer
import kotlinx.serialization.internal.StringSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.list
import kotlinx.serialization.map
import okhttp3.logging.HttpLoggingInterceptor
import com.yoox.net.models.inbound.Item as InboundItem
import com.yoox.net.models.inbound.SearchResults as InboundSearchResults

private const val AUTHORITY: String = "secure.api.yoox.biz"
private const val API_BASE_URL: String = "https://$AUTHORITY/YooxCore.API/1.0/"
private const val DIVISION_CODE: String = "YOOX"
private const val VISUAL_SEARCH_BASE_URL =
    "https://ynappi-dev.azurewebsites.net/api/detected_items/IT"

class ItemsBuilder(private val country: String) {
    fun build(): Items =
        Items(OkHttpEngine(OkHttpConfig().apply {
            addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
        }), country)

    internal fun build(engine: HttpClientEngine): Items =
        Items(engine, country)
}

class Items(
    private val engine: HttpClientEngine,
    private val country: String
) {
    private val client by lazy {
        HttpClient(engine) {
            install(JsonFeature) {
                serializer = KotlinxSerializer(Json.nonstrict)
            }
        }
    }

    fun get(id: String): Request<Item> =
        KtorRequest.Get(
            client,
            URLBuilder("$API_BASE_URL${DIVISION_CODE}_${country.toUpperCase()}/items/$id"),
            InboundItem.serializer()
        )
            .map(InboundItem::toOutboundItem)

    fun search(department: DepartmentType): FilterableRequest =
        DepartmentSearchRequest(
            client,
            URLBuilder("$API_BASE_URL${DIVISION_CODE}_${country.toUpperCase()}/SearchResults?dept=${department.value}")
        )
}

interface FilterableRequest : Request<SearchResults> {
    fun filterBy(vararg filters: Filter): FilterableRequest

    fun filterBy(filter: PriceFilter): FilterableRequest

    fun page(index: Int): FilterableRequest

    fun filterBy(freeText: String): FilterableRequest

    fun clone(): FilterableRequest
}

internal val attributesSerializer =
    (StringSerializer to StringSerializer.list).map

typealias ListOfAttributes = Map<String, List<String>>

internal class DepartmentSearchRequest internal constructor(
    private val client: HttpClient,
    internal val uri: URLBuilder
) : FilterableRequest {
    override fun clone(): FilterableRequest {
        return DepartmentSearchRequest(client, uri.clone())
    }

    override suspend fun execute(): SearchResults =
        KtorRequest.Get(
            client,
            uri,
            InboundSearchResults.serializer()
        ).map(InboundSearchResults::toOutboundSearchResults)
            .execute()

    override fun filterBy(vararg filters: Filter): DepartmentSearchRequest =
        filter("attributes",
            attributesSerializer,
            { it },
            { it })(filters.groupBy({ it.field }, { it.value }))

    override fun filterBy(filter: PriceFilter): FilterableRequest {
        uri.parameters["priceMin"] = filter.min.toString()
        uri.parameters["priceMax"] = filter.max.toString()
        return DepartmentSearchRequest(
            client,
            uri
        )
    }

    override fun filterBy(freeText: String): FilterableRequest {
        uri.parameters["textSearch"] = freeText
        return DepartmentSearchRequest(
            client,
            uri
        )
    }

    override fun page(index: Int): FilterableRequest {
        uri.parameters["page"] = index.toString()
        return DepartmentSearchRequest(
            client,
            uri
        )
    }

    private fun List<Pair<String, List<String>>>.flattenAttributes(): ListOfAttributes =
        groupBy({ it.first }, { it.second })
            .mapValues { it.value.flatten() }

    private fun <T> filter(
        key: String,
        serializer: KSerializer<T>,
        output: (ListOfAttributes) -> T,
        input: (T) -> ListOfAttributes
    ): (ListOfAttributes) -> DepartmentSearchRequest {
        return { next ->
            val previous = input(
                Json.parse(
                    serializer,
                    uri.parameters[key] ?: "{}"
                )
            )
            val union =
                (previous.toList() + next.toList()).flattenAttributes()
                    .toList()
                    .associateBy({ it.first }, { it.second.distinct() })
            uri.parameters[key] =
                Json.stringify(
                    serializer,
                    output(union)
                )
            DepartmentSearchRequest(
                client,
                uri
            )
        }
    }
}