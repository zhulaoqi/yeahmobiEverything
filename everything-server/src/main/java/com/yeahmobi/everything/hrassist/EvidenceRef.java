package com.yeahmobi.everything.hrassist;

/**
 * Evidence reference for traceability.
 */
public record EvidenceRef(
        String id,
        String caseId,
        String sourceType,
        String sourcePathOrUrl,
        String snippet,
        double confidence,
        String createdAt
) {
}

