package com.trendwave.retry_service.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FailureCode {

    // Hard declines
    INSUFFICIENT_FUNDS       ("Insufficient funds on card",                 DeclineType.HARD,      0,  0.0),
    CARD_EXPIRED             ("Card has expired",                           DeclineType.HARD,      0,  0.0),
    CARD_BLOCKED             ("Card reported blocked or stolen",            DeclineType.HARD,      0,  0.0),
    INVALID_CVV              ("Invalid CVV provided",                       DeclineType.HARD,      0,  0.0),
    CARD_NOT_ACTIVATED       ("Card not activated",                         DeclineType.HARD,      0,  0.0),
    DO_NOT_HONOR             ("Generic hard decline from issuer",           DeclineType.HARD,      0,  0.0),
    INVALID_CARD_NUMBER      ("Card number invalid",                        DeclineType.HARD,      0,  0.0),

    // Soft declines
    ISSUER_TIMEOUT           ("Issuer did not respond in time",             DeclineType.SOFT,      10, 0.65),
    RATE_LIMIT               ("Too many requests — issuer rate limiting",   DeclineType.SOFT,      30, 0.55),
    VELOCITY_CHECK           ("Risk velocity check triggered",              DeclineType.SOFT,      60, 0.45),
    TEMPORARY_PROCESSOR_ERROR("Processor returned temporary error",         DeclineType.SOFT,      15, 0.60),
    TRY_AGAIN                ("Issuer responded with try-again signal",     DeclineType.SOFT,      10, 0.70),
    NETWORK_ERROR            ("Network error during authorization",         DeclineType.SOFT,      5,  0.72),
    ISSUER_UNAVAILABLE       ("Issuer system temporarily unavailable",      DeclineType.SOFT,      20, 0.58),
    RISK_SCORE_HIGH          ("Transaction flagged by risk engine",         DeclineType.SOFT,      30, 0.40),

    // Ambiguous
    UNKNOWN_ERROR            ("Unknown error code returned",                DeclineType.AMBIGUOUS, 15, 0.30),
    GENERIC_DECLINE          ("Generic decline — reason unknown",           DeclineType.AMBIGUOUS, 20, 0.25);

    private final String description;
    private final DeclineType declineType;
    private final int defaultRetryDelaySeconds;
    private final double baseRecoveryRate;
}