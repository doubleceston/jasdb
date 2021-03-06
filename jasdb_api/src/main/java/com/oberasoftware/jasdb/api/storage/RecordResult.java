/*
 * The JASDB software and code is Copyright protected 2011 and owned by Renze de Vries
 * 
 * All the code and design principals in the codebase are also Copyright 2011 
 * protected and owned Renze de Vries. Any unauthorized usage of the code or the 
 * design and principals as in this code is prohibited.
 */
package com.oberasoftware.jasdb.api.storage;

import com.oberasoftware.jasdb.api.exceptions.JasDBStorageException;

/**
 * This contains a single record result from storage.
 */
public interface RecordResult {
    /**
     * Gets an inputstream allowing reading of the record entity
     * @return The record stream
     * @throws JasDBStorageException If unable to stream the record
     */
    ClonableDataStream getStream() throws JasDBStorageException;

    /**
     * The size of the record
     * @return The size of the record
     */
    long getRecordSize();

    /**
     * Returns whether a record is found
     * @return True if a record is found, False if not
     */
    boolean isRecordFound();
}
