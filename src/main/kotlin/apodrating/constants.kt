package apodrating

const val OPERATION_PUT_RATING = "putRating"
const val OPERATION_GET_RATING = "getRatingSuspend"
const val OPERATION_GET_APOD_FOR_DATE = "getApodForDate"
const val OPERATION_GET_APODS = "getApods"
const val OPERATION_POST_APOD = "postApod"
const val API_AUTH_KEY = "ApiKeyAuth"

const val EVENTBUS_ADDRESS = "apodQuery"

const val LOCATION_HEADER = "Location"

const val API_KEY_HEADER = "X-API-KEY"

const val PARAM_APOD_ID = "apodId"

const val CTX_FIELD_APOD = "apodFromDB"

const val STATIC_PATH = "/ui/*"

const val CACHE_ALIAS = "apodCache"

const val CIRCUIT_BREAKER_NAME = "apod-circuit-breaker"

const val HEAP_POOL_SIZE = 10L
