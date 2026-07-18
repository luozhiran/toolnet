package com.itg.net.request.business

interface ApiEnvelopeParser {
    fun parse(rawBody: String?): ApiEnvelope
}
