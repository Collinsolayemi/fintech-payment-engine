package com.fintech.engine.service;

import com.fintech.engine.dto.*;
import java.util.List;

public interface FxService {
    List<FxRateResponse> getLiveRates(String baseCurrency);
    FxQuoteResponse createQuote(String userEmailOrId, FxQuoteRequest request);
    FxExchangeResponse executeQuote(String userEmailOrId, ExecuteFxExchangeRequest request, String idempotencyKey);
    List<FxQuoteResponse> getUserQuotes(String userEmailOrId);
}

