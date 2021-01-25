/*
 * (C) Copyright 2019 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Florent Guillaume
 */
package org.nuxeo.ecm.blob.s3;

import static com.amazonaws.SDKGlobalConfiguration.ACCESS_KEY_ENV_VAR;
import static com.amazonaws.SDKGlobalConfiguration.ALTERNATE_ACCESS_KEY_ENV_VAR;
import static com.amazonaws.SDKGlobalConfiguration.ALTERNATE_SECRET_KEY_ENV_VAR;
import static com.amazonaws.SDKGlobalConfiguration.AWS_REGION_ENV_VAR;
import static com.amazonaws.SDKGlobalConfiguration.AWS_SESSION_TOKEN_ENV_VAR;
import static com.amazonaws.SDKGlobalConfiguration.SECRET_KEY_ENV_VAR;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.isNoneBlank;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.nuxeo.ecm.blob.s3.S3BlobStoreConfiguration.AWS_ID_PROPERTY;
import static org.nuxeo.ecm.blob.s3.S3BlobStoreConfiguration.AWS_SECRET_PROPERTY;
import static org.nuxeo.ecm.blob.s3.S3BlobStoreConfiguration.AWS_SESSION_TOKEN_PROPERTY;
import static org.nuxeo.ecm.blob.s3.S3BlobStoreConfiguration.BUCKET_NAME_PROPERTY;
import static org.nuxeo.ecm.blob.s3.S3BlobStoreConfiguration.BUCKET_PREFIX_PROPERTY;
import static org.nuxeo.ecm.blob.s3.S3BlobStoreConfiguration.BUCKET_REGION_PROPERTY;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.SimpleMessage;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ComparisonFailure;
import org.junit.Test;
import org.nuxeo.common.utils.Vars;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.blob.AbstractBlobStore;
import org.nuxeo.ecm.core.blob.BlobContext;
import org.nuxeo.ecm.core.blob.BlobInfo;
import org.nuxeo.ecm.core.blob.BlobProvider;
import org.nuxeo.ecm.core.blob.BlobStore;
import org.nuxeo.ecm.core.blob.BlobStoreBlobProvider;
import org.nuxeo.ecm.core.blob.CachingBlobStore;
import org.nuxeo.ecm.core.blob.InMemoryBlobStore;
import org.nuxeo.ecm.core.blob.KeyStrategyDigest;
import org.nuxeo.ecm.core.blob.SimpleManagedBlob;
import org.nuxeo.ecm.core.blob.TestAbstractBlobStore;
import org.nuxeo.ecm.core.blob.TransactionalBlobStore;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.LogCaptureFeature;
import org.nuxeo.runtime.transaction.TransactionHelper;

@Features(LogCaptureFeature.class)
public abstract class TestS3BlobStoreAbstract extends TestAbstractBlobStore {

    @Inject
    protected LogCaptureFeature.Result logResult;

    protected static boolean propertiesSet;

    @BeforeClass
    public static void beforeClass() {
        getProperties().forEach(TestS3BlobStoreAbstract::setProperty);
        propertiesSet = true;
    }

    @AfterClass
    public static void afterClass() {
        if (propertiesSet) {
            getProperties().keySet().forEach(TestS3BlobStoreAbstract::removeProperty);
            propertiesSet = false;
        }
    }

    public static void setProperty(String key, String value) {
        System.getProperties().put(S3BlobStoreConfiguration.SYSTEM_PROPERTY_PREFIX + '.' + key, value);
    }

    public static void removeProperty(String key) {
        System.getProperties().remove(S3BlobStoreConfiguration.SYSTEM_PROPERTY_PREFIX + '.' + key);
    }

    public static Map<String, String> getProperties() {
        Map<String, String> properties = new HashMap<>();

        String envId = defaultIfBlank(System.getenv(ACCESS_KEY_ENV_VAR), System.getenv(ALTERNATE_ACCESS_KEY_ENV_VAR));
        String envSecret = defaultIfBlank(System.getenv(SECRET_KEY_ENV_VAR),
                System.getenv(ALTERNATE_SECRET_KEY_ENV_VAR));
        String envSessionToken = defaultIfBlank(System.getenv(AWS_SESSION_TOKEN_ENV_VAR), "");
        String envRegion = defaultIfBlank(System.getenv(AWS_REGION_ENV_VAR), "");

        String bucketName = "nuxeo-test-changeme";
        String bucketPrefix = "testfolder/";

        assumeTrue("AWS Credentials not set in the environment variables", isNoneBlank(envId, envSecret));

        properties.put(AWS_ID_PROPERTY, envId);
        properties.put(AWS_SECRET_PROPERTY, envSecret);
        properties.put(AWS_SESSION_TOKEN_PROPERTY, envSessionToken);
        properties.put(BUCKET_REGION_PROPERTY, envRegion);
        properties.put(BUCKET_NAME_PROPERTY, bucketName);
        properties.put(BUCKET_PREFIX_PROPERTY, bucketPrefix);
        return properties;
    }

    @Override
    public void clearBlobStore() throws IOException {
        super.clearBlobStore();

        BlobProvider otherbp = blobManager.getBlobProvider("other");
        BlobStore otherbs = ((BlobStoreBlobProvider) otherbp).store;
        clearBlobStore(otherbs);
    }

    @Override
    protected void clearBlobStore(BlobStore blobStore) throws IOException {
        super.clearBlobStore(blobStore);
        if (blobStore instanceof S3BlobStore) {
            clearBlobStore((S3BlobStore) blobStore);
        }
    }

    // remove all objects, including versions
    protected void clearBlobStore(S3BlobStore blobStore) {
        blobStore.clearBucket();
    }

    protected boolean isTransactional() {
        return bs instanceof TransactionalBlobStore;
    }

    protected boolean hasCache() {
        BlobStore s = bs;
        if (s instanceof TransactionalBlobStore) {
            s = ((TransactionalBlobStore) s).store;
        }
        return s instanceof CachingBlobStore;
    }

    // copy/move from another S3BlobStore has an different, optimized code path

    @Test
    public void testCopyIsOptimized() {
        BlobProvider otherbp = blobManager.getBlobProvider("other");
        BlobStore otherS3Store = ((BlobStoreBlobProvider) otherbp).store; // no need for unwrap
        assertTrue(bs.copyBlobIsOptimized(otherS3Store));
        InMemoryBlobStore otherStore = new InMemoryBlobStore("mem", new KeyStrategyDigest("MD5"));
        assertFalse(bs.copyBlobIsOptimized(otherStore));
    }

    @Test
    public void testCopyFromS3BlobStore() throws IOException {
        testCopyOrMoveFromS3BlobStore(false);
    }

    @Test
    public void testMoveFromS3BlobStore() throws IOException {
        testCopyOrMoveFromS3BlobStore(true);
    }

    protected void testCopyOrMoveFromS3BlobStore(boolean atomicMove) throws IOException {
        // we don't test the unimplemented copyBlob API, as it's only called from commit or during caching
        assumeFalse("low-level copy/move not tested in transactional blob store", bp.isTransactional());

        BlobProvider otherbp = blobManager.getBlobProvider("other");
        BlobStore sourceStore = ((BlobStoreBlobProvider) otherbp).store;
        String key1 = useDeDuplication() ? FOO_MD5 : ID1;
        String key2 = useDeDuplication() ? key1 : ID2;
        assertFalse(bs.copyBlob(key2, sourceStore, key1, atomicMove));
        assertEquals(key1, sourceStore.writeBlob(blobContext(ID1, FOO)));
        assertTrue(bs.copyBlob(key2, sourceStore, key1, atomicMove));
        assertBlob(bs, key2, FOO);
        if (atomicMove) {
            assertNoBlob(sourceStore, key1);
        } else {
            assertBlob(sourceStore, key1, FOO);
        }
    }

    protected void logTrace(String message) {
        LogEvent event = Log4jLogEvent.newBuilder() //
                                      .setMessage(new SimpleMessage(message))
                                      .build();
        logResult.getCaughtEvents().add(event);
    }

    protected void nextTransaction(boolean next) {
        if (next && TransactionHelper.isTransactionActiveOrMarkedRollback()) {
            TransactionHelper.commitOrRollbackTransaction();
            TransactionHelper.startTransaction();
        }
    }

    @Test
    @LogCaptureFeature.FilterOn(loggerClass = AbstractBlobStore.class, logLevel = "TRACE")
    public void testCRUDTracing() throws IOException {
        testCRUDTracing(false);
    }

    protected void testCRUDTracing(boolean manyTransactions) throws IOException {

        logResult.clear();

        // store blob
        logTrace("== Write ==");
        BlobContext blobContext = blobContext(ID1, FOO);
        String key1 = bp.writeBlob(blobContext);
        assertKey(ID1, key1);
        nextTransaction(manyTransactions);
        // check content
        logTrace("== Read ==");
        assertBlobStoreReadBlob(bs, key1, FOO);

        if (useDeDuplication()) {
            // write same again
            logTrace("== Write same again ==");
            bp.writeBlob(blobContext);
            nextTransaction(manyTransactions);
        }

        // copy
        logTrace("== Copy ==");
        // create source blob
        BlobInfo blobInfo = new BlobInfo();
        blobInfo.key = key1;
        Blob blob1 = new SimpleManagedBlob(bp.getId(), blobInfo);
        // blob context for write into other doc
        blobContext = new BlobContext(blob1, ID2, XPATH);
        // write into other blob provider
        BlobProvider otherbp = blobManager.getBlobProvider("other");
        String key2 = otherbp.writeBlob(blobContext);
        // check content
        logTrace("== Read copy ==");
        BlobStore bs2 = ((BlobStoreBlobProvider) otherbp).store;
        assertBlobStoreReadBlob(bs2, key2, FOO);
        nextTransaction(manyTransactions);

        if (hasCache() && (!isTransactional() || manyTransactions)) {
            // clear cache if any to test cache re-fill
            // (don't clear cache in the middle of a transaction)
            clearCache(bs);
            clearCache(bs2);
            // check content
            logTrace("== Read after cache clear ==");
            assertBlobStoreReadBlob(bs, key1, FOO);
            assertBlobStoreReadBlob(bs2, key2, FOO);
        }

        // replace
        logTrace("== Write second blob ==");
        String key3 = bs.writeBlob(blobContext(ID1, BAR));
        assertKey(ID1, key3);
        nextTransaction(manyTransactions);
        // check content
        logTrace("== Read second blob ==");
        assertBlobStoreReadBlob(bs, key3, BAR);

        if (!useDeDuplication()) {
            // delete
            logTrace("== Delete second blob ==");
            bs.deleteBlob(blobContext(key3, null));
            nextTransaction(manyTransactions);
            // check deleted
            assertNoBlobReadBlob(bs, key3);
        }

        nextTransaction(true);

        String filename;
        if (!hasCache()) {
            filename = "trace-testcrud-nocaching.txt";
        } else if (!isTransactional()) {
            filename = "trace-testcrud.txt";
        } else {
            if (TransactionHelper.isTransactionActive()) {
                if (manyTransactions) {
                    filename = "trace-testcrud-record-manytx.txt";
                } else {
                    filename = "trace-testcrud-record.txt";
                }
            } else {
                filename = "trace-testcrud-record-notx.txt";
            }
        }
        List<String> expectedTrace = readTrace(filename);
        List<String> actualTrace = logResult.getCaughtEventMessages();
        Map<String, String> context = new HashMap<>();
        // System.err.println(filename);
        // System.err.println(String.join("\n", actualTrace));
        assertEqualsLists(expectedTrace, actualTrace, context);
    }

    protected List<String> readTrace(String filename) throws IOException {
        URL url = getClass().getClassLoader().getResource(filename);
        if (url == null) {
            throw new IOException(filename);
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(Paths.get(url.toURI()));
        } catch (URISyntaxException e) {
            throw new IOException(filename, e);
        }
        for (ListIterator<String> it = lines.listIterator(); it.hasNext();) {
            String line = it.next();
            if (line.startsWith(" ") || line.startsWith("\t")) {
                line = line.trim();
                it.set(line);
            }
            // skip @startuml, @enduml and ' comments, and plantuml statements
            if (line.startsWith("@") || line.startsWith("'") || line.startsWith("participant ")) {
                it.remove();
            }
        }
        return lines;
    }

    protected static void assertEqualsLists(List<String> expectedList, List<String> actualList,
            Map<String, String> context) {
        int size = Math.min(expectedList.size(), actualList.size());
        for (int i = 0; i < size; i++) {
            String expected = expectedList.get(i);
            String actual = actualList.get(i);
            matchAndCaptureVars(expected, actual, context, i + 1);
        }
        if (expectedList.size() > size) {
            fail("at line " + (size + 1) + ": Missing line: " + expectedList.get(size));
        }
        if (actualList.size() > size) {
            fail("at line " + (size + 1) + ": Unexpected line: " + actualList.get(size));
        }
    }

    // the possible names for vars
    protected static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([0-9a-zA-Z_.]+)}");

    // the possible values that a captured var is allowed to match
    protected static final String VAR_MATCHED = "[0-9a-zA-Z_.]+";

    protected static void matchAndCaptureVars(String expected, String actual, Map<String, String> context, int line) {
        // extract var names to capture and turn into regex
        List<String> vars = new ArrayList<>();
        Matcher varMatcher = VAR_PATTERN.matcher(expected);
        // build a regex where everything is quoted except for holes to match vars
        StringBuffer regexBuilder = new StringBuffer("\\Q");
        while (varMatcher.find()) {
            String var = varMatcher.group(1);
            String value = context.get(var);
            String replacement;
            if (value == null) {
                // create a non-quoted hole and put a regex with a group to match a var
                // (in appendReplacement backslashes need to be escaped)
                replacement = "\\\\E(" + VAR_MATCHED + ")\\\\Q";
                vars.add(var);
            } else {
                replacement = value;
            }
            varMatcher.appendReplacement(regexBuilder, replacement);
        }
        varMatcher.appendTail(regexBuilder);
        regexBuilder.append("\\E");
        String regex = regexBuilder.toString();
        Matcher m = Pattern.compile(regex).matcher(actual);
        if (!m.matches()) {
            if (vars.isEmpty()) {
                throw new ComparisonFailure("at line " + line, Vars.expand(expected, context), actual);
            } else {
                throw new ComparisonFailure("at line " + line + ": Could not match", expected, actual);
            }
        }
        // collect captured vars
        for (int i = 0; i < m.groupCount(); i++) {
            String var = vars.get(i);
            String value = m.group(i + 1);
            context.put(var, value);
        }
    }

    // internal test
    @Test
    public void testAssertEqualsLists() {
        Map<String, String> context = new HashMap<>();
        assertEqualsLists(Arrays.asList("foo", "bar"), Arrays.asList("foo", "bar"), context);
        // capture variable
        assertEqualsLists(Arrays.asList("foo${NAME}bar"), Arrays.asList("foogeebar"), context);
        assertEquals("gee", context.get("NAME"));
        // match variable
        assertEqualsLists(Arrays.asList("hello${NAME}"), Arrays.asList("hellogee"), context);
    }

    // internal test
    @Test
    public void testAssertEqualsListsFailures() {
        Map<String, String> context = new HashMap<>();
        try {
            assertEqualsLists(Arrays.asList("foo"), Arrays.asList("bar"), context);
            fail();
        } catch (AssertionError e) {
            assertEquals("at line 1 expected:<[foo]> but was:<[bar]>", e.getMessage());
        }
        try {
            assertEqualsLists(Arrays.asList(), Arrays.asList("bar"), context);
            fail();
        } catch (AssertionError e) {
            assertEquals("at line 1: Unexpected line: bar", e.getMessage());
        }
        try {
            assertEqualsLists(Arrays.asList("foo"), Arrays.asList(), context);
            fail();
        } catch (AssertionError e) {
            assertEquals("at line 1: Missing line: foo", e.getMessage());
        }
        // failed captures
        try {
            assertEqualsLists(Arrays.asList("foo${VAR}"), Arrays.asList("bar"), context);
            fail();
        } catch (AssertionError e) {
            assertEquals("at line 1: Could not match expected:<[foo${VAR}]> but was:<[bar]>", e.getMessage());
        }
        context.put("VAR", "foo");
        try {
            assertEqualsLists(Arrays.asList("${VAR}"), Arrays.asList("bar"), context);
            fail();
        } catch (AssertionError e) {
            assertEquals("at line 1 expected:<[foo]> but was:<[bar]>", e.getMessage());
        }
    }

}
