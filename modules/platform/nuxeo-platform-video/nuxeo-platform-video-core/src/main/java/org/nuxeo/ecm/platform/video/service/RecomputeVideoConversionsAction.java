/*
 * (C) Copyright 2021 Nuxeo (http://nuxeo.com/) and others.
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
 *     Charles Boidot
 */
package org.nuxeo.ecm.platform.video.service;

import static org.nuxeo.ecm.core.api.CoreSession.ALLOW_VERSION_WRITE;
import static org.nuxeo.ecm.core.api.versioning.VersioningService.DISABLE_AUTO_CHECKOUT;
import static org.nuxeo.ecm.core.bulk.BulkServiceImpl.STATUS_STREAM;
import static org.nuxeo.ecm.core.bulk.action.SetPropertiesAction.PARAM_DISABLE_AUDIT;
import static org.nuxeo.ecm.platform.video.VideoConstants.TRANSCODED_VIDEOS_PROPERTY;
import static org.nuxeo.ecm.platform.video.service.VideoConversionWork.VIDEO_CONVERSIONS_DONE_EVENT;
import static org.nuxeo.lib.stream.computation.AbstractComputation.INPUT_1;
import static org.nuxeo.lib.stream.computation.AbstractComputation.OUTPUT_1;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentNotFoundException;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.bulk.action.computation.AbstractBulkComputation;
import org.nuxeo.ecm.core.bulk.message.BulkCommand;
import org.nuxeo.ecm.core.convert.api.ConversionException;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.platform.video.TranscodedVideo;
import org.nuxeo.ecm.platform.video.Video;
import org.nuxeo.ecm.platform.video.VideoDocument;
import org.nuxeo.lib.stream.computation.Topology;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.stream.StreamProcessorTopology;
import org.nuxeo.runtime.transaction.TransactionHelper;

/**
 * BAF Computation that fills video renditions for the blob property described by the given xpath.
 *
 * @since 11.5
 */
public class RecomputeVideoConversionsAction implements StreamProcessorTopology {

    private static final Logger log = LogManager.getLogger(RecomputeVideoConversionsAction.class);

    public static final String ACTION_NAME = "recomputeVideoConversion";

    // @since 11.4
    public static final String ACTION_FULL_NAME = "bulk/" + ACTION_NAME;

    public static final String PARAM_XPATH = "xpath";

    public static final String PARAM_CONVERSION_NAMES = "conversionNames";

    @Override
    public Topology getTopology(Map<String, String> options) {
        return Topology.builder()
                       .addComputation(RecomputeRenditionsComputation::new, //
                               Arrays.asList(INPUT_1 + ":" + ACTION_FULL_NAME, OUTPUT_1 + ":" + STATUS_STREAM))
                       .build();
    }

    public static class RecomputeRenditionsComputation extends AbstractBulkComputation {

        protected String xpath;

        protected List<String> conversionNames;

        protected VideoService videoService = Framework.getService(VideoService.class);

        public RecomputeRenditionsComputation() {
            super(ACTION_FULL_NAME);
        }

        @Override
        public void startBucket(String bucketKey) {
            BulkCommand command = getCurrentCommand();
            xpath = command.getParam(PARAM_XPATH);
            conversionNames = command.getParam(PARAM_CONVERSION_NAMES);
            if (conversionNames.isEmpty()) {
                conversionNames = videoService.getAvailableVideoConversions()
                                              .stream()
                                              .map(VideoConversion::getName)
                                              .collect(Collectors.toList());
            }
        }

        @Override
        protected void compute(CoreSession session, List<String> ids, Map<String, Serializable> properties) {
            log.debug("Compute action: {} for doc ids: {}", ACTION_NAME, ids);
            for (String docId : ids) {
                if (!session.exists(new IdRef(docId))) {
                    log.debug("Doc id doesn't exist: {}", docId);
                    continue;
                }

                DocumentModel workingDocument = session.getDocument(new IdRef(docId));
                Property fileProp = workingDocument.getProperty(xpath);
                if (!(fileProp instanceof Blob)) {
                    log.warn("Property: {} of doc id: {} is not a blob.", xpath, docId);
                }
                Blob blob = (Blob) fileProp.getValue();
                if (blob == null) {
                    // do nothing
                    log.debug("No blob for doc: {}", workingDocument);
                    continue;
                }
                try {
                    VideoDocument videoDoc = workingDocument.getAdapter(VideoDocument.class);
                    Video video = videoDoc.getVideo();

                    for (String conversion : conversionNames) {
                        // here we want to commit the transaction since the video conversion can be very long
                        TransactionHelper.commitOrRollbackTransaction();
                        TranscodedVideo transcodedVideo = null;
                        try {
                            transcodedVideo = videoService.convert(video, conversion);
                        } catch (ConversionException e) {
                            log.warn("Conversion: {} of doc id: {} has failed", conversion, docId);
                        }
                        if (!TransactionHelper.isTransactionActive()) {
                            TransactionHelper.startTransaction();
                        }
                        saveRendition(session, new IdRef(workingDocument.getId()), conversion, transcodedVideo);
                    }
                } catch (DocumentNotFoundException e) {
                    // a parent of the document may have been deleted.
                    continue;
                } finally {
                    if (!TransactionHelper.isTransactionActive()) {
                        TransactionHelper.startTransaction();
                    }
                }
                workingDocument = session.saveDocument(workingDocument);
                DocumentEventContext ctx = new DocumentEventContext(session, session.getPrincipal(), workingDocument);
                Event event = ctx.newEvent(VIDEO_CONVERSIONS_DONE_EVENT);
                Framework.getService(EventService.class).fireEvent(event);
            }
        }

        @SuppressWarnings("unchecked")
        protected void saveRendition(CoreSession session, IdRef docId, String conversionName,
                TranscodedVideo transcodedVideo) {
            DocumentModel doc = session.getDocument(docId);
            var transcodedVideos = (List<Map<String, Serializable>>) doc.getPropertyValue(TRANSCODED_VIDEOS_PROPERTY);
            transcodedVideos.removeIf(tv -> conversionName.equals(tv.get("name")));
            if (transcodedVideo != null) {
                transcodedVideos.add(transcodedVideo.toMap());
            }
            doc.setPropertyValue(TRANSCODED_VIDEOS_PROPERTY, (Serializable) transcodedVideos);
            if (doc.isVersion()) {
                doc.putContextData(ALLOW_VERSION_WRITE, Boolean.TRUE);
            }
            doc.putContextData("disableNotificationService", Boolean.TRUE);
            doc.putContextData(PARAM_DISABLE_AUDIT, Boolean.TRUE);
            doc.putContextData(DISABLE_AUTO_CHECKOUT, Boolean.TRUE);
            session.saveDocument(doc);
        }
    }
}
