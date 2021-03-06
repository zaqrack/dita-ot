/*
 * This file is part of the DITA Open Toolkit project.
 *
 * Copyright 2014 Jarno Elovirta
 *
 * See the accompanying LICENSE file for applicable license.
 */
package org.dita.dost.module;

import org.dita.dost.exception.DITAOTException;
import org.dita.dost.pipeline.AbstractPipelineInput;
import org.dita.dost.pipeline.AbstractPipelineOutput;
import org.dita.dost.util.Configuration;
import org.dita.dost.util.Job.FileInfo;
import org.dita.dost.util.Job.FileInfo.Filter;
import org.dita.dost.util.XMLUtils;
import org.dita.dost.writer.CoderefResolver;
import org.dita.dost.writer.NormalizeTableFilter;
import org.dita.dost.writer.TopicFragmentFilter;
import org.xml.sax.XMLFilter;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.dita.dost.util.Constants.ANT_INVOKER_EXT_PARAM_PROCESSING_MODE;
import static org.dita.dost.util.Constants.ATTRIBUTE_NAME_HREF;
import static org.dita.dost.util.Constants.ATTR_FORMAT_VALUE_DITA;

/** @deprecated since 2.3 */
@Deprecated
final class TopicFragmentModule extends AbstractPipelineModuleImpl {

    public static final String SKIP_CODEREF = "preprocess.coderef.skip";

    private Configuration.Mode processingMode;
    private boolean resolveCoderef;

    /**
     * Process topic files for same topic fragments identifiers.
     * 
     * @param input Input parameters and resources.
     * @return always returns {@code null}
     */
    @Override
    public AbstractPipelineOutput execute(final AbstractPipelineInput input)
            throws DITAOTException {
        final String mode = input.getAttribute(ANT_INVOKER_EXT_PARAM_PROCESSING_MODE);
        processingMode = mode != null ? Configuration.Mode.valueOf(mode.toUpperCase()) : Configuration.Mode.LAX;
        resolveCoderef = !Boolean.parseBoolean(input.getAttribute(SKIP_CODEREF));

        final Collection<FileInfo> fis = job.getFileInfo(new Filter<FileInfo>() {
            @Override
            public boolean accept(final FileInfo f) {
                return ATTR_FORMAT_VALUE_DITA.equals(f.format);
            }
        });
        for (final FileInfo f: fis) {
            final URI file = job.tempDirURI.resolve(f.uri);
            logger.info("Processing " + file);
            try {
                XMLUtils.transform(file, getProcessingPipe(file));
            } catch (final DITAOTException e) {
                logger.error("Failed to process same topic fragment identifiers: " + e.getMessage(), e);
            }
        }
        return null;
    }

    /**
     * Get pipe line filters
     *
     * @param fileToParse absolute URI to current file being processed
     */
    private List<XMLFilter> getProcessingPipe(final URI fileToParse) {
        assert fileToParse.isAbsolute();

        final List<XMLFilter> pipe = new ArrayList<>();

        final TopicFragmentFilter filter = new TopicFragmentFilter(ATTRIBUTE_NAME_HREF);
        pipe.add(filter);

        final NormalizeTableFilter normalizeFilter = new NormalizeTableFilter();
        normalizeFilter.setLogger(logger);
        normalizeFilter.setProcessingMode(processingMode);
        pipe.add(normalizeFilter);

        if (resolveCoderef) {
            final CoderefResolver coderefFilter = new CoderefResolver();
            coderefFilter.setJob(job);
            coderefFilter.setLogger(logger);
            coderefFilter.setCurrentFile(fileToParse);
            pipe.add(coderefFilter);
        }

        return pipe;
    }

}
