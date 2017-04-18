/*******************************************************************************
 * (c) Copyright 2017 Hewlett-Packard Development Company, L.P.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License v2.0 which accompany this distribution.
 *
 * The Apache License is available at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 *******************************************************************************/
package io.cloudslang.content.couchbase.entities.couchbase;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Created by TusaM
 * 4/14/2017.
 */
public enum ConflictResolutionType {
    LWW("lww"),
    SEQNO("seqno");

    private final String value;

    ConflictResolutionType(String value) {
        this.value = value;
    }

    public static String getConflictResolutionType(String input) throws RuntimeException {
        if (isBlank(input)) {
            return SEQNO.getValue();
        }

        for (ConflictResolutionType resolution : ConflictResolutionType.values()) {
            if (resolution.getValue().equalsIgnoreCase(input)) {
                return resolution.getValue();
            }
        }

        throw new RuntimeException("Invalid Couchbase conflict resolution type value: [" + input + "]. Valid values: lww, seqno.");
    }

    private String getValue() {
        return value;
    }
}