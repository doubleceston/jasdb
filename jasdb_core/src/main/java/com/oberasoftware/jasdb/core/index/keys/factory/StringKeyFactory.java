/*
 * The JASDB software and code is Copyright protected 2011 and owned by Renze de Vries
 * 
 * All the code and design principals in the codebase are also Copyright 2011 
 * protected and owned Renze de Vries. Any unauthorized usage of the code or the 
 * design and principals as in this code is prohibited.
 */
package com.oberasoftware.jasdb.core.index.keys.factory;

import com.oberasoftware.jasdb.api.index.keys.KeyFactory;
import com.oberasoftware.jasdb.api.session.IndexableItem;
import com.oberasoftware.jasdb.api.exceptions.JasDBStorageException;
import com.oberasoftware.jasdb.api.storage.DataBlock;
import com.oberasoftware.jasdb.api.storage.DataBlockResult;
import com.oberasoftware.jasdb.api.index.MemoryConstants;
import com.oberasoftware.jasdb.api.index.keys.Key;
import com.oberasoftware.jasdb.core.index.keys.LongKey;
import com.oberasoftware.jasdb.core.index.keys.StringKey;
import com.oberasoftware.jasdb.api.index.keys.KeyLoadResult;
import com.oberasoftware.jasdb.api.index.keys.KeyType;
import com.oberasoftware.jasdb.core.index.keys.keyinfo.KeyLoadResultImpl;
import com.oberasoftware.jasdb.core.index.keys.types.StringKeyType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

public class StringKeyFactory extends AbstractKeyFactory implements KeyFactory {
	private static final Logger LOG = LoggerFactory.getLogger(StringKeyFactory.class);
	
	private String field;
	private int maxSize;
    private int memorySize;
	
	public StringKeyFactory(String field, String maxSize) {
        super(field);
		this.field = field;
		
		try {
			this.maxSize = Integer.parseInt(maxSize);
            this.memorySize = MemoryConstants.ARRAY_BYTE_SIZE + MemoryConstants.OBJECT_BYTE_SIZE + this.maxSize;
		} catch(NumberFormatException e) {
			LOG.error("Unable to determine max string key size", e);
		}
	}
	
	@Override
	public Key loadKey(int curPosition, ByteBuffer byteBuffer) throws JasDBStorageException {
		byte[] keyBuffer = new byte[maxSize];
		byteBuffer.position(curPosition);
		byteBuffer.get(keyBuffer, 0, maxSize);

        return new StringKey(keyBuffer);
	}

	@Override
	public void writeKey(Key key, int curPosition, ByteBuffer byteBuffer) throws JasDBStorageException {
		if(key instanceof StringKey) {
			StringKey stringKey = (StringKey) key;

            byte[] keyBytes = stringKey.getUnicodeBytes();
            if(keyBytes.length > maxSize) {
                throw new JasDBStorageException("Key is too big to be stored in index, byte size: " + keyBytes.length + " max allowed: " + maxSize);
            } else {
                byteBuffer.position(curPosition);
                byteBuffer.put(keyBytes);
            }
		} else {
			throw new JasDBStorageException("The key is of an unexpected type: " + key.getClass().toString());
		}
	}

    @Override
    public KeyLoadResult loadKey(int offset, DataBlock dataBlock) throws JasDBStorageException {
        DataBlockResult<byte[]> keyBuffer = dataBlock.loadBytes(offset);

        byte[] value = keyBuffer.getValue();
        if(value.length == 1 && value[0] == Byte.MAX_VALUE) {
            //empty nil key
            return new KeyLoadResultImpl(createEmptyKey(), keyBuffer.getEndBlock(), keyBuffer.getNextOffset());
        } else {
            return new KeyLoadResultImpl(new StringKey(value), keyBuffer.getEndBlock(), keyBuffer.getNextOffset());
        }
    }

    @Override
    public DataBlock writeKey(Key key, DataBlock dataBlock) throws JasDBStorageException {
        if(key instanceof StringKey) {
            StringKey stringKey = (StringKey) key;

            byte[] keyBytes = stringKey.getUnicodeBytes();
            if(keyBytes != null) {
                if (keyBytes.length > maxSize) {
                    throw new JasDBStorageException("Key is too big to be stored in index, byte size: " + keyBytes.length + " max allowed: " + maxSize);
                } else {
                    return dataBlock.writeBytes(keyBytes).getDataBlock();
                }
            } else {
                return dataBlock.writeBytes(new byte[]{Byte.MAX_VALUE}).getDataBlock();
            }
        } else {
            throw new JasDBStorageException("The key is of an unexpected type: " + key.getClass().toString());
        }
    }

    @Override
	public Key createKey(IndexableItem indexableItem) throws JasDBStorageException {
		Object value = indexableItem.getValue(field);
        return convertToKey(value);
	}

    @Override
    public Key createEmptyKey() {
        return new StringKey((byte[]) null);
    }

    @Override
    protected Key convertToKey(Object value) throws JasDBStorageException {
        if(value != null) {
            String fieldValue = value.toString();
            StringKey key = new StringKey(fieldValue);
            if(key.getUnicodeBytes().length > maxSize) {
                throw new JasDBStorageException("Field size has been exceeded: " + key.getUnicodeBytes().length + " max length: " + maxSize);
            } else {
                return key;
            }
        } else {
            return createEmptyKey();
        }
    }

    @Override
	public Key convertKey(Key key) throws JasDBStorageException {
        if(key instanceof StringKey) {
            return key;
        } else if(key instanceof LongKey) {
			return new StringKey(key.getValue().toString());
		} else {
            //unsupported conversion
			return key;
		}
	}

	@Override
	public boolean supportsKey(Key key) {
        return key instanceof StringKey;
	}

	@Override
	public String asHeader() {
        return getFieldName() + "(" + getKeyId() + ":" + maxSize + ");";
	}

    @Override
    public KeyType getKeyType() {
        return new StringKeyType(maxSize);
    }

    @Override
	public String getKeyId() {
		return StringKeyType.KEY_ID;
	}

	@Override
	public int getKeySize() {
		return this.maxSize;
	}

    @Override
    public int getMemorySize() {
        return memorySize;
    }

    @Override
	public String getFieldName() {
		return this.field;
	}
}
