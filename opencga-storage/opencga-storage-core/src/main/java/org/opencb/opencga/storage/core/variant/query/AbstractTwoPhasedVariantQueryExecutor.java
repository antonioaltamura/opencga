package org.opencb.opencga.storage.core.variant.query;

import com.google.common.collect.Iterators;
import org.apache.commons.lang3.time.StopWatch;
import org.opencb.biodata.models.variant.Variant;
import org.opencb.commons.datastore.core.ObjectMap;
import org.opencb.commons.datastore.core.Query;
import org.opencb.commons.datastore.core.QueryOptions;
import org.opencb.opencga.core.common.TimeUtils;
import org.opencb.opencga.core.results.VariantQueryResult;
import org.opencb.opencga.storage.core.metadata.VariantStorageMetadataManager;
import org.opencb.opencga.storage.core.variant.adaptors.iterators.VariantDBIteratorWithCounts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.opencb.opencga.storage.core.variant.VariantStorageEngine.Options.APPROXIMATE_COUNT;
import static org.opencb.opencga.storage.core.variant.adaptors.VariantQueryParam.REGION;
import static org.opencb.opencga.storage.core.variant.adaptors.VariantQueryUtils.isValidParam;

/**
 * Execute a variant query in two phases.
 *
 * Query first a primary source (e.g. an index) and then apply a transformation
 * (e.g. post-filter, or fetch from the variants storage) to obtain the final result.
 *
 * Created by jacobo on 25/04/19.
 */
public abstract class AbstractTwoPhasedVariantQueryExecutor extends VariantQueryExecutor {

    public static final float MAGIC_NUMBER = 12.5F; // Magic number! Proportion of variants from chr1 and the whole genome
    private final String primarySource;

    private Logger logger = LoggerFactory.getLogger(AbstractTwoPhasedVariantQueryExecutor.class);

    public AbstractTwoPhasedVariantQueryExecutor(VariantStorageMetadataManager metadataManager, String storageEngineId,
                                                 ObjectMap options, String primarySource) {
        super(metadataManager, storageEngineId, options);
        this.primarySource = primarySource;
    }

    /**
     * Count number of results from the primary.
     * @param query   Query
     * @param options Options
     * @return        Number of variants in the primary source.
     */
    protected abstract int primaryCount(Query query, QueryOptions options);

    protected final void setNumTotalResults(VariantDBIteratorWithCounts variants, VariantQueryResult<Variant> result,
                                            Query query, QueryOptions options) {
        setNumTotalResults(variants, result, query, options, null);
    }

    protected final void setNumTotalResults(VariantDBIteratorWithCounts variantsFromPrimary, VariantQueryResult<Variant> result,
                                            Query query, QueryOptions options, Integer numIntersectQueries) {
        // TODO: Allow exact count with "approximateCount=false"
        if (!options.getBoolean(QueryOptions.SKIP_COUNT, true) || options.getBoolean(APPROXIMATE_COUNT.key(), false)) {
            int sampling = variantsFromPrimary.getCount();
            int limit = options.getInt(QueryOptions.LIMIT, 0);
            int skip = options.getInt(QueryOptions.SKIP, 0);
            if (limit > 0 && limit > result.getNumResults()) {
                if (skip > 0 && result.getNumResults() == 0) {
                    // Skip could be greater than numTotalResults. Approximate count
                    result.setApproximateCount(true);
                } else {
                    // Less results than limit. Count is not approximated
                    result.setApproximateCount(false);
                }
                result.setNumTotalResults(result.getNumResults() + skip);
            } else if (variantsFromPrimary.hasNext()) {
                long totalCount;
                if (!isValidParam(query, REGION)) {
                    int chr1Count;
                    StopWatch stopWatch = StopWatch.createStarted();
                    if (variantsFromPrimary.getChromosomeCount("1") != null) {
                        // Iterate until the chr1 is exhausted
                        int i = 0;
                        while ("1".equals(variantsFromPrimary.getCurrentChromosome()) && variantsFromPrimary.hasNext()) {
                            variantsFromPrimary.next();
                            i++;
                        }
                        chr1Count = variantsFromPrimary.getChromosomeCount("1");
                        if (i != 0) {
                            logger.info("Count variants from chr1 using the same iterator over the " + primarySource + " : "
                                    + "Read " + i + " extra variants in " + TimeUtils.durationToString(stopWatch));
                        }
                    } else {
                        query.put(REGION.key(), "1");
                        chr1Count = primaryCount(query, new QueryOptions());
                        logger.info("Count variants from chr1 in " + primarySource + " : " + TimeUtils.durationToString(stopWatch));
                    }

                    logger.info("chr1 count = " + chr1Count);
                    totalCount = (int) (chr1Count * MAGIC_NUMBER);
//                } else if (sampleIndexDBAdaptor.isFastCount(sampleIndexQuery) && sampleIndexQuery.getSamplesMap().size() == 1) {
//                    StopWatch stopWatch = StopWatch.createStarted();
//                    Map.Entry<String, List<String>> entry = sampleIndexQuery.getSamplesMap().entrySet().iterator().next();
//                    totalCount = sampleIndexDBAdaptor.count(sampleIndexQuery, entry.getKey());
//                    logger.info("Count variants from sample index table : " + TimeUtils.durationToString(stopWatch));
                } else {
                    StopWatch stopWatch = StopWatch.createStarted();
                    Iterators.getLast(variantsFromPrimary);
                    totalCount = variantsFromPrimary.getCount();
                    logger.info("Drain variants from " + primarySource + " : " + TimeUtils.durationToString(stopWatch));
                }
                long approxCount;
                logger.info("totalCount = " + totalCount);
                logger.info("result.getNumResults() = " + result.getNumResults());
                logger.info("numQueries = " + numIntersectQueries);
                if (numIntersectQueries != null && numIntersectQueries == 1) {
                    // Just one query with limit, index was accurate enough
                    approxCount = totalCount;
                } else {
                    // Multiply first to avoid loss of precision
                    approxCount = totalCount * result.getNumResults() / sampling;
                    logger.info("sampling = " + sampling);
                }
                logger.info("approxCount = " + approxCount);
                result.setApproximateCount(true);
                result.setNumTotalResults(approxCount);
                result.setApproximateCountSamplingSize(sampling);
            } else {
                logger.info(primarySource + " Iterator exhausted");
                logger.info("sampling = " + sampling);
                result.setApproximateCount(sampling != result.getNumResults());
                result.setNumTotalResults(sampling);
            }
        }
    }
}
