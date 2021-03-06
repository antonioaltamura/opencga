/*
 * Copyright 2015-2017 OpenCB
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
 */

package org.opencb.opencga.storage.core.utils;

import org.opencb.biodata.models.core.Gene;
import org.opencb.biodata.models.core.Region;
import org.opencb.cellbase.client.rest.CellBaseClient;
import org.opencb.cellbase.core.api.GeneDBAdaptor;
import org.opencb.commons.datastore.core.Query;
import org.opencb.commons.datastore.core.QueryOptions;
import org.opencb.commons.datastore.core.QueryResponse;
import org.opencb.commons.datastore.core.QueryResult;
import org.opencb.opencga.storage.core.variant.adaptors.VariantQueryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created on 30/03/17.
 *
 * @author Jacobo Coll &lt;jacobo167@gmail.com&gt;
 */
public class CellBaseUtils {

    private static Logger logger = LoggerFactory.getLogger(CellBaseUtils.class);
    private static final int GENE_EXTRA_REGION = 5000;
    private final CellBaseClient cellBaseClient;
    private final String assembly;
    public static final QueryOptions GENE_QUERY_OPTIONS = new QueryOptions(QueryOptions.INCLUDE,
            "id,name,chromosome,start,end,transcripts.id,transcripts.name,transcripts.proteinId");

    private ConcurrentHashMap<String, Region> cache = new ConcurrentHashMap<>();

    public CellBaseUtils(CellBaseClient cellBaseClient, String assembly) {
        this.cellBaseClient = cellBaseClient;
        this.assembly = assembly;
    }

    public Region getGeneRegion(String geneStr) {
        List<Region> regions = getGeneRegion(Collections.singletonList(geneStr), false);
        if (regions.isEmpty()) {
            return null;
        } else {
            return regions.get(0);
        }
    }

    public List<Region> getGeneRegion(List<String> geneStrs, boolean skipMissing) {
        geneStrs = new LinkedList<>(geneStrs);
        List<Region> regions = new ArrayList<>(geneStrs.size());
        Iterator<String> iterator = geneStrs.iterator();
        while (iterator.hasNext()) {
            String gene = iterator.next();
            Region region = cache.get(gene);
            if (region != null) {
                regions.add(region);
                iterator.remove();
            }
        }
        if (geneStrs.isEmpty()) {
            return regions;
        }
        try {
            long ts = System.currentTimeMillis();
            QueryOptions options = new QueryOptions(GENE_QUERY_OPTIONS); // Copy options. DO NOT REUSE QUERY OPTIONS
            QueryResponse<Gene> response = cellBaseClient.getGeneClient().get(geneStrs, options);
            logger.info("Query genes from CellBase " + cellBaseClient.getSpecies() + ":" + assembly + " " + geneStrs + "  -> "
                    + (System.currentTimeMillis() - ts) / 1000.0 + "s ");
            List<String> missingGenes = null;
            for (QueryResult<Gene> result : response.getResponse()) {
                Gene gene = null;
                String geneStr = result.getId();
                // It may happen that CellBase returns more than 1 result for the same gene name.
                // Pick the gene where the given geneStr matches with the name,id,transcript.id,transcript.name or transcript.proteinId
                if (result.getResult().size() > 1) {
                    for (Gene aGene : result.getResult()) {
                        if (geneStr.equals(aGene.getName())
                                || geneStr.equals(aGene.getId())
                                || aGene.getTranscripts().stream().anyMatch(t -> geneStr.equals(t.getName()))
                                || aGene.getTranscripts().stream().anyMatch(t -> geneStr.equals(t.getId()))
                                || aGene.getTranscripts().stream().anyMatch(t -> geneStr.equals(t.getProteinID()))) {
//                            if (gene != null) {
//                                // More than one gene found!
//                                // Leave gene empty, so it is marked as "not found"
//                                gene = null;
//                                break;
//                            }
                            gene = aGene;
                            break;
                        }
                    }
                } else {
                    gene = result.first();
                }
                if (gene == null) {
                    Query query = new Query();
                    if (geneStr.startsWith("ENSG")) {
                        query.put("id", geneStr);
                    } else if (geneStr.startsWith("ENST")) {
                        query.put("transcripts.id", geneStr);
                    } else {
                        query.put("name", geneStr);
                    }
                    gene = cellBaseClient.getGeneClient().search(query, options).getResponse().get(0).first();
                }
                if (gene == null) {
                    if (missingGenes == null) {
                        missingGenes = new ArrayList<>();
                    }
                    missingGenes.add(result.getId());
                    continue;
                }
                int start = Math.max(0, gene.getStart() - GENE_EXTRA_REGION);
                int end = gene.getEnd() + GENE_EXTRA_REGION;
                Region region = new Region(gene.getChromosome(), start, end);
                regions.add(region);
                cache.put(gene.getName(), region);
                cache.put(gene.getId(), region);
                cache.put(geneStr, region);
            }
            if (!skipMissing && missingGenes != null) {
                throw VariantQueryException.geneNotFound(String.join(",", missingGenes));
            }
            return regions;
        } catch (IOException e) {
            throw VariantQueryException.internalException(e);
        }
    }

    public Set<String> getGenesByGo(List<String> goValues) {
        Set<String> genes = new HashSet<>();
        QueryOptions params = new QueryOptions(QueryOptions.INCLUDE, "name,chromosome,start,end");
        try {
            List<QueryResult<Gene>> responses = cellBaseClient.getGeneClient().get(goValues, params)
                    .getResponse();
            for (QueryResult<Gene> response : responses) {
                for (Gene gene : response.getResult()) {
                    genes.add(gene.getName());
                }
            }
        } catch (IOException e) {
            throw VariantQueryException.internalException(e);
        }
        return genes;
    }

    public Set<String> getGenesByExpression(List<String> expressionValues) {
        Set<String> genes = new HashSet<>();
        QueryOptions params = new QueryOptions(QueryOptions.INCLUDE, "name,chromosome,start,end");

        // The number of results for each expression value may be huge. Query one by one
        for (String expressionValue : expressionValues) {
            try {
                String[] split = expressionValue.split(":");
                expressionValue = split[0];
                Query cellbaseQuery = new Query(2)
                        .append(GeneDBAdaptor.QueryParams.ANNOTATION_EXPRESSION_TISSUE.key(), expressionValue)
                        .append(GeneDBAdaptor.QueryParams.ANNOTATION_EXPRESSION_VALUE.key(), "UP");
                List<QueryResult<Gene>> responses = cellBaseClient.getGeneClient().search(cellbaseQuery, params)
                        .getResponse();
                for (QueryResult<Gene> response : responses) {
                    for (Gene gene : response.getResult()) {
                        genes.add(gene.getName());
                    }
                }
            } catch (IOException e) {
                throw VariantQueryException.internalException(e);
            }
        }
        return genes;
    }

    public CellBaseClient getCellBaseClient() {
        return cellBaseClient;
    }
}
