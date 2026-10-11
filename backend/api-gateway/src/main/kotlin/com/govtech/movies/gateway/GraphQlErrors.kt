package com.govtech.movies.gateway

import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import graphql.schema.DataFetchingEnvironment
import io.grpc.Status
import io.grpc.StatusRuntimeException
import org.slf4j.LoggerFactory
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter
import org.springframework.graphql.execution.ErrorType
import org.springframework.stereotype.Component

/**
 * Turns exceptions into GraphQL errors the frontend can show to the user.
 * Messages from INVALID_ARGUMENT and NOT_FOUND are written for users by the services.
 * Anything unexpected gets a generic message, so internal details do not leak.
 */
@Component
class GraphQlErrors : DataFetcherExceptionResolverAdapter() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun resolveToSingleError(ex: Throwable, env: DataFetchingEnvironment): GraphQLError? {
        val (type, message) = when (ex) {
            is BadInputException -> ErrorType.BAD_REQUEST to ex.message
            is StatusRuntimeException -> when (ex.status.code) {
                Status.Code.INVALID_ARGUMENT -> ErrorType.BAD_REQUEST to ex.status.description
                Status.Code.NOT_FOUND -> ErrorType.NOT_FOUND to ex.status.description
                Status.Code.RESOURCE_EXHAUSTED -> ErrorType.BAD_REQUEST to "The upload is too large"
                Status.Code.UNAVAILABLE, Status.Code.DEADLINE_EXCEEDED -> {
                    log.warn("Backend service unavailable: {}", ex.status)
                    ErrorType.INTERNAL_ERROR to "A backend service is unavailable. Please try again shortly."
                }
                else -> {
                    log.error("gRPC call failed", ex)
                    ErrorType.INTERNAL_ERROR to "Something went wrong on the server"
                }
            }
            else -> return null // let Spring's default handler return a generic error
        }
        return GraphqlErrorBuilder.newError(env).errorType(type).message(message).build()
    }
}
