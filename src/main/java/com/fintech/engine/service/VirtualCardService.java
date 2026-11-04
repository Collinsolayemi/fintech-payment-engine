package com.fintech.engine.service;

import com.fintech.engine.dto.*;
import java.util.List;
import java.util.UUID;

public interface VirtualCardService {
    VirtualCardResponse issueCard(String userEmailOrId, IssueCardRequest request);
    List<VirtualCardResponse> getUserCards(String userEmailOrId);
    CardDetailsResponse getCardDetails(String userEmailOrId, UUID cardId);
    VirtualCardResponse updateCardStatus(String userEmailOrId, UUID cardId, UpdateCardStatusRequest request);
    VirtualCardResponse updateCardControls(String userEmailOrId, UUID cardId, UpdateCardControlsRequest request);
}

