package org.opencb.opencga.analysis.variant.knockout;

import com.google.common.collect.Iterables;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.opencb.biodata.models.core.Region;
import org.opencb.biodata.models.feature.Genotype;
import org.opencb.biodata.models.variant.StudyEntry;
import org.opencb.biodata.models.variant.Variant;
import org.opencb.biodata.models.variant.avro.*;
import org.opencb.commons.datastore.core.DataResult;
import org.opencb.commons.datastore.core.Query;
import org.opencb.commons.datastore.core.QueryOptions;
import org.opencb.opencga.analysis.variant.knockout.result.KnockoutByGene;
import org.opencb.opencga.analysis.variant.knockout.result.KnockoutBySample;
import org.opencb.opencga.analysis.variant.knockout.result.KnockoutBySample.KnockoutGene;
import org.opencb.opencga.analysis.variant.knockout.result.KnockoutTranscript;
import org.opencb.opencga.analysis.variant.knockout.result.KnockoutVariant;
import org.opencb.opencga.analysis.variant.manager.VariantCatalogQueryUtils;
import org.opencb.opencga.analysis.variant.manager.VariantStorageManager;
import org.opencb.opencga.analysis.variant.manager.VariantStorageToolExecutor;
import org.opencb.opencga.core.tools.annotations.ToolExecutor;
import org.opencb.opencga.core.common.TimeUtils;
import org.opencb.opencga.core.models.sample.Sample;
import org.opencb.opencga.storage.core.metadata.models.Trio;
import org.opencb.opencga.storage.core.variant.adaptors.GenotypeClass;
import org.opencb.opencga.storage.core.variant.adaptors.VariantField;
import org.opencb.opencga.storage.core.variant.adaptors.VariantQueryParam;
import org.opencb.opencga.storage.core.variant.adaptors.VariantQueryUtils;
import org.opencb.opencga.storage.core.variant.adaptors.iterators.VariantDBIterator;
import org.opencb.opencga.storage.core.variant.query.VariantQueryParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;

import static org.opencb.cellbase.core.variant.annotation.VariantAnnotationUtils.PROTEIN_CODING;
import static org.opencb.opencga.analysis.variant.manager.VariantCatalogQueryUtils.COMPOUND_HETEROZYGOUS;
import static org.opencb.opencga.storage.core.variant.adaptors.VariantQueryUtils.ALL;
import static org.opencb.opencga.storage.core.variant.adaptors.VariantQueryUtils.IS;

@ToolExecutor(id = "opencga-local",
        tool = KnockoutAnalysis.ID,
        source = ToolExecutor.Source.STORAGE,
        framework = ToolExecutor.Framework.LOCAL)
public class KnockoutLocalAnalysisExecutor extends KnockoutAnalysisExecutor implements VariantStorageToolExecutor {

    private Logger logger = LoggerFactory.getLogger(this.getClass());
    private VariantStorageManager variantStorageManager;
    private boolean allProteinCoding;
    private List<String> biotype;

    @Override
    protected void run() throws Exception {
        variantStorageManager = getVariantStorageManager();
        biotype = StringUtils.isEmpty(getBiotype()) ? null : Arrays.asList(getBiotype().split(","));
        if (biotype != null && biotype.contains(PROTEIN_CODING)) {
            biotype = new ArrayList<>(biotype);
            biotype.remove(PROTEIN_CODING);
        }
        allProteinCoding = getProteinCodingGenes().size() == 1 && getProteinCodingGenes().iterator().next().equals(ALL);

        String executionMethod = getExecutorParams().getString("executionMethod", "auto");
        boolean bySample;
        switch (executionMethod) {
            case "bySample":
                bySample = true;
                break;
            case "byGene":
                if (allProteinCoding) {
                    throw new IllegalArgumentException("Unable to execute '" + executionMethod + "' "
                            + "when analysing all protein coding genes");
                }
                bySample = false;
                break;
            case "auto":
            case "":
                if (allProteinCoding) {
                    bySample = true;
                } else if (getSamples().size() < (getProteinCodingGenes().size() + getOtherGenes().size())) {
                    bySample = true;
                } else {
                    bySample = false;
                }
                break;
            default:
                throw new IllegalArgumentException("Unknown executionMethod '" + executionMethod + "'");
        }
//        if (bySample || (auto && (allProteinCoding || (getProteinCodingGenes().size() + getOtherGenes().size()) >= getSamples().size()))) {
        if (bySample) {
            logger.info("Execute knockout analysis by sample");
            addAttribute("executionMethod", "bySample");
            new KnockoutBySampleExecutor().run();
        } else {
            logger.info("Execute knockout analysis by gene");
            addAttribute("executionMethod", "byGene");
            new KnockoutByGeneExecutor().run();
        }
    }

    private class KnockoutBySampleExecutor {
        public void run() throws Exception {
            Query baseQuery = new Query()
                    .append(VariantQueryParam.STUDY.key(), getStudy())
                    .append(VariantQueryParam.FILTER.key(), getFilter())
                    .append(VariantQueryParam.QUAL.key(), getQual());
            for (String sample : getSamples()) {
                StopWatch stopWatch = StopWatch.createStarted();
                logger.info("Processing sample {}", sample);
                Map<String, KnockoutGene> knockoutGenes = new LinkedHashMap<>();
                Trio trio = getTrios().get(sample);

                // Protein coding genes (if any)
                if (allProteinCoding) {
                    // All protein coding genes
                    Query query = new Query(baseQuery)
                            .append(VariantQueryParam.ANNOT_BIOTYPE.key(), PROTEIN_CODING)
                            .append(VariantQueryParam.INCLUDE_SAMPLE.key(), sample)
                            .append(VariantQueryParam.ANNOT_CONSEQUENCE_TYPE.key(), getCt());
                    knockouts(query, sample, trio, knockoutGenes,
                            getCts()::contains,
                            b -> b.equals(PROTEIN_CODING),
                            g -> true);
                } else if (!getProteinCodingGenes().isEmpty()) {
                    // Set of protein coding genes
                    Query query = new Query(baseQuery)
                            .append(VariantQueryParam.GENE.key(), getProteinCodingGenes())
                            .append(VariantQueryParam.ANNOT_BIOTYPE.key(), PROTEIN_CODING)
                            .append(VariantQueryParam.ANNOT_CONSEQUENCE_TYPE.key(), getCt())
                            .append(VariantQueryParam.INCLUDE_SAMPLE.key(), sample);
                    knockouts(query, sample, trio, knockoutGenes,
                            getCts()::contains,
                            b -> b.equals(PROTEIN_CODING),
                            getProteinCodingGenes()::contains);
                }

                // Other genes (if any)
                if (!getOtherGenes().isEmpty()) {
                    Query query = new Query(baseQuery)
                            .append(VariantQueryParam.ANNOT_BIOTYPE.key(), biotype)
                            .append(VariantQueryParam.GENE.key(), getOtherGenes())
                            .append(VariantQueryParam.INCLUDE_SAMPLE.key(), sample);
                    knockouts(query, sample, trio, knockoutGenes,
                            ct -> true,  // Accept any CT
                            biotype == null ? (b -> !b.equals(PROTEIN_CODING)) : new HashSet<>(biotype)::contains,
                            getOtherGenes()::contains);
                }

                if (knockoutGenes.isEmpty()) {
                    logger.info("No results for sample {}", sample);
                } else {
                    KnockoutBySample bySample = buildGeneKnockoutBySample(sample, knockoutGenes);
                    writeSampleFile(bySample);
                }
                logger.info("Sample {} processed in {}", sample, TimeUtils.durationToString(stopWatch));
                logger.info("-----------------------------------------------------------");
            }

            transposeSampleToGeneOutputFiles();
        }

        private void transposeSampleToGeneOutputFiles() throws IOException {
            Map<String, KnockoutByGene> byGeneMap = new HashMap<>();
            for (String sample : getSamples()) {
                Path fileName = getSampleFileName(sample);
                if (Files.exists(fileName)) {
                    KnockoutBySample bySample = readSampleFile(sample);
                    for (KnockoutGene gene : bySample.getGenes()) {
                        KnockoutByGene byGene = byGeneMap.computeIfAbsent(gene.getName(),
                                id -> new KnockoutByGene().setId(gene.getId()).setName(gene.getName()).setSamples(new LinkedList<>()));

                        KnockoutByGene.KnockoutSample knockoutSample = new KnockoutByGene.KnockoutSample()
                                .setId(bySample.getSample().getId())
                                .setTranscripts(gene.getTranscripts());
                        byGene.getSamples().add(knockoutSample);
                    }
                }
            }

            for (Map.Entry<String, KnockoutByGene> entry : byGeneMap.entrySet()) {
                writeGeneFile(entry.getValue());
            }
        }

        private void knockouts(Query query, String sample, Trio trio, Map<String, KnockoutGene> knockoutGenes,
                               Predicate<String> ctFilter,
                               Predicate<String> biotypeFilter,
                               Predicate<String> geneFilter) throws Exception {
            homAltKnockouts(sample, knockoutGenes, query, ctFilter, biotypeFilter, geneFilter);

            multiAllelicKnockouts(sample, knockoutGenes, query, ctFilter, biotypeFilter, geneFilter);

            if (trio != null) {
                compHetKnockouts(sample, trio, knockoutGenes, query, ctFilter, biotypeFilter, geneFilter);
            }

            structuralKnockouts(sample, knockoutGenes, query, ctFilter, biotypeFilter, geneFilter);
        }

        protected void homAltKnockouts(String sample,
                                       Map<String, KnockoutGene> knockoutGenes,
                                       Query query,
                                       Predicate<String> ctFilter,
                                       Predicate<String> biotypeFilter,
                                       Predicate<String> geneFilter)
                throws Exception {
            query = new Query(query)
                    .append(VariantQueryParam.INCLUDE_FORMAT.key(), "GT," + VariantQueryParser.FILE_IDX)
                    .append(VariantQueryParam.GENOTYPE.key(), sample + IS + "1/1");

            int numVariants = iterate(query, v -> {
                StudyEntry studyEntry = v.getStudies().get(0);
                List<String> sampleData = studyEntry.getSamplesData().get(0);
                FileEntry fileEntry = studyEntry.getFiles().get(Integer.parseInt(sampleData.get(1)));
                for (ConsequenceType consequenceType : v.getAnnotation().getConsequenceTypes()) {
                    if (validCt(consequenceType, ctFilter, biotypeFilter, geneFilter)) {
                        addGene(v.toString(), sampleData.get(0),
                                fileEntry, consequenceType, knockoutGenes, KnockoutVariant.KnockoutType.HOM_ALT);
                    }
                }
            });
            logger.debug("Read {} HOM_ALT variants from sample {}", numVariants, sample);
        }

        protected void multiAllelicKnockouts(String sample,
                                             Map<String, KnockoutGene> knockoutGenes,
                                             Query query,
                                             Predicate<String> ctFilter,
                                             Predicate<String> biotypeFilter,
                                             Predicate<String> geneFilter)
                throws Exception {

            query = new Query(query)
                    .append(VariantQueryParam.INCLUDE_FORMAT.key(), "GT," + VariantQueryParser.FILE_IDX)
                    .append(VariantQueryParam.GENOTYPE.key(), sample + IS + "1/2");

            Map<String, KnockoutVariant> variants = new HashMap<>();
            iterate(query, variant -> {
                Variant secVar = getSecondaryVariant(variant);
                StudyEntry studyEntry = variant.getStudies().get(0);
                List<String> sampleData = studyEntry.getSamplesData().get(0);
                FileEntry fileEntry = studyEntry.getFiles().get(Integer.parseInt(sampleData.get(1)));
                KnockoutVariant knockoutVariant = new KnockoutVariant(
                        variant.toString(),
                        sampleData.get(0),
                        fileEntry.getAttributes().get(StudyEntry.FILTER),
                        fileEntry.getAttributes().get(StudyEntry.QUAL),
                        KnockoutVariant.KnockoutType.MULTI_ALLELIC, null
                );
                if (variants.put(variant.toString(), knockoutVariant) == null) {
                    // Variant not seen
                    if (variant.overlapWith(secVar, true)) {
                        // Add overlapping variant.
                        // If the overlapping variant is ever seen (so it also matches the filter criteria),
                        // the gene will be selected as knockout.
                        variants.put(secVar.toString(), new KnockoutVariant());
                    }
                } else {
                    KnockoutVariant secKnockoutVar = variants.get(secVar.toString());
                    // The variant was already seen. i.e. there was a variant with this variant as secondary alternate
                    for (ConsequenceType consequenceType : variant.getAnnotation().getConsequenceTypes()) {
                        if (validCt(consequenceType, ctFilter, biotypeFilter, geneFilter)) {
                            String gt = studyEntry.getSamplesData().get(0).get(0);
                            addGene(variant.toString(), gt, knockoutVariant.getFilter(), knockoutVariant.getQual(),
                                    consequenceType, knockoutGenes, KnockoutVariant.KnockoutType.MULTI_ALLELIC
                            );
                            addGene(secVar.toString(), secKnockoutVar.getGenotype(), secKnockoutVar.getFilter(), secKnockoutVar.getQual(),
                                    consequenceType, knockoutGenes, KnockoutVariant.KnockoutType.MULTI_ALLELIC
                            );
                        }
                    }
                }
            });
        }

        protected void compHetKnockouts(String sample, Trio family,
                                        Map<String, KnockoutGene> knockoutGenes,
                                        Query query,
                                        Predicate<String> ctFilter,
                                        Predicate<String> biotypeFilter,
                                        Predicate<String> geneFilter)
                throws Exception {
            query = new Query(query)
                    .append(VariantCatalogQueryUtils.FAMILY.key(), family.getId())
                    .append(VariantCatalogQueryUtils.FAMILY_DISORDER.key(), getDisorder())
                    .append(VariantCatalogQueryUtils.FAMILY_PROBAND.key(), sample)
                    .append(VariantCatalogQueryUtils.FAMILY_SEGREGATION.key(), COMPOUND_HETEROZYGOUS)
                    .append(VariantQueryParam.INCLUDE_SAMPLE.key(), family.toList())
                    .append(VariantQueryParam.INCLUDE_FORMAT.key(), "GT," + VariantQueryParser.FILE_IDX);

            int numVariants = iterate(query, v -> {
                StudyEntry studyEntry = v.getStudies().get(0);
                List<String> sampleData = studyEntry.getSamplesData().get(0);
                FileEntry fileEntry = studyEntry.getFiles().get(Integer.parseInt(sampleData.get(1)));
                for (ConsequenceType consequenceType : v.getAnnotation().getConsequenceTypes()) {
                    if (validCt(consequenceType, ctFilter, biotypeFilter, geneFilter)) {
                        addGene(v.toString(), sampleData.get(0),
                                fileEntry, consequenceType, knockoutGenes, KnockoutVariant.KnockoutType.COMP_HET);
                    }
                }
            });
            logger.debug("Read " + numVariants + " COMP_HET variants from sample " + sample);
        }

        protected void structuralKnockouts(String sample,
                                           Map<String, KnockoutGene> knockoutGenes,
                                           Query baseQuery,
                                           Predicate<String> ctFilter,
                                           Predicate<String> biotypeFilter,
                                           Predicate<String> geneFilter) throws Exception {
            Query query = new Query(baseQuery)
                    .append(VariantQueryParam.SAMPLE.key(), sample)
                    .append(VariantQueryParam.TYPE.key(), VariantType.DELETION)
                    .append(VariantQueryParam.INCLUDE_FORMAT.key(), "GT," + VariantQueryParser.FILE_IDX);
//                .append(VariantQueryParam.ANNOT_CONSEQUENCE_TYPE.key(), LOF + "," + VariantAnnotationUtils.FEATURE_TRUNCATION);
//        Set<String> cts = new HashSet<>(LOF_SET);
//        cts.add(VariantAnnotationUtils.FEATURE_TRUNCATION);

            iterate(query, svVariant -> {
                Set<String> transcripts = new HashSet<>(svVariant.getAnnotation().getConsequenceTypes().size());
                for (ConsequenceType consequenceType : svVariant.getAnnotation().getConsequenceTypes()) {
                    if (validCt(consequenceType, ctFilter, biotypeFilter, geneFilter)) {
                        transcripts.add(consequenceType.getEnsemblTranscriptId());
                    }
                }
                Query thisSvQuery = new Query(baseQuery)
                        .append(VariantQueryParam.SAMPLE.key(), sample)
                        .append(VariantQueryParam.REGION.key(), new Region(svVariant.getChromosome(), svVariant.getStart(), svVariant.getEnd()));

                List<String> svSampleData = svVariant.getStudies().get(0).getSamplesData().get(0);
                FileEntry svFileEntry = svVariant.getStudies().get(0).getFiles().get(Integer.parseInt(svSampleData.get(1)));

                iterate(thisSvQuery, variant -> {
                    if (variant.sameGenomicVariant(svVariant)) {
                        return;
                    }
                    StudyEntry studyEntry = variant.getStudies().get(0);
                    List<String> sampleData = studyEntry.getSamplesData().get(0);
                    FileEntry fileEntry = studyEntry.getFiles().get(Integer.parseInt(sampleData.get(1)));
                    for (ConsequenceType consequenceType : variant.getAnnotation().getConsequenceTypes()) {
                        if (validCt(consequenceType, ctFilter, biotypeFilter, geneFilter)) {
                            if (transcripts.contains(consequenceType.getEnsemblTranscriptId())) {
                                addGene(variant.toString(), svSampleData.get(0), svFileEntry, consequenceType, knockoutGenes,
                                        KnockoutVariant.KnockoutType.DELETION_OVERLAP);
                                addGene(svVariant.toString(), sampleData.get(0), fileEntry, consequenceType, knockoutGenes,
                                        KnockoutVariant.KnockoutType.DELETION_OVERLAP);
                            }
                        }
                    }
                });
            });
        }
    }

    private class KnockoutByGeneExecutor {

        protected void run() throws Exception {
            Query baseQuery = new Query()
                    .append(VariantQueryParam.STUDY.key(), getStudy())
                    .append(VariantQueryParam.INCLUDE_SAMPLE.key(), getSamples())
                    .append(VariantQueryParam.INCLUDE_GENOTYPE.key(), true);

            for (String gene : getProteinCodingGenes()) {
                knockoutGene(new Query(baseQuery)
                                .append(VariantQueryParam.GENE.key(), gene)
                                .append(VariantQueryParam.ANNOT_CONSEQUENCE_TYPE.key(), getCts())
                                .append(VariantQueryParam.ANNOT_BIOTYPE.key(), PROTEIN_CODING), gene,
                        getCts()::contains,
                        PROTEIN_CODING::equals);
            }
            for (String gene : getOtherGenes()) {
                knockoutGene(new Query(baseQuery)
                                .append(VariantQueryParam.GENE.key(), gene)
                                .append(VariantQueryParam.STUDY.key(), getStudy())
                                .append(VariantQueryParam.ANNOT_BIOTYPE.key(), biotype), gene,
                        c -> true,
                        biotype == null ? b -> !b.equals(PROTEIN_CODING) : new HashSet<>(biotype)::contains);
            }

            transposeGeneToSampleOutputFiles();
        }

        private void knockoutGene(Query baseQuery, String gene, Predicate<String> ctFilter, Predicate<String> biotypeFilter) throws Exception {
            KnockoutByGene knockout;
            StopWatch stopWatch = StopWatch.createStarted();
            if (getGeneFileName(gene).toFile().exists()) {
                knockout = readGeneFile(gene);
            } else {
                knockout = new KnockoutByGene();
            }
            knockout.setName(gene);

            Map<String, Integer> compHetCandidateSamples = new HashMap<>();
            Map<String, Integer> multiAllelicCandidateSamples = new HashMap<>();
            Map<String, Integer> deletionCandidateSamples = new HashMap<>();

            Query query = new Query(baseQuery)
                    .append(VariantQueryParam.INCLUDE_SAMPLE.key(), VariantQueryUtils.NONE)
                    .append(VariantQueryParam.INCLUDE_FILE.key(), VariantQueryUtils.NONE);
            int numVariants = iterate(query, new QueryOptions(VariantField.SUMMARY, true), v -> {
                int limit = 1000;
                int skip = 0;
                int numSamples;
                do {
                    DataResult<Variant> result = variantStorageManager.getSampleData(v.toString(), getStudy(),
                            new QueryOptions(QueryOptions.LIMIT, limit)
                                    .append(QueryOptions.SKIP, skip)
                                    .append(VariantQueryParam.INCLUDE_SAMPLE.key(), getSamples())
                                    .append(VariantQueryParam.INCLUDE_FILE.key(), null)
                                    .append(VariantQueryParam.GENOTYPE.key(), GenotypeClass.MAIN_ALT), getToken());
                    Variant variant = result.first();
                    numSamples = variant.getStudies().get(0).getSamplesData().size();
                    skip += numSamples;
                    logger.info("[{}] - Found {} samples at variant {}", gene,
                            numSamples, v.toString());

                    VariantAnnotation annotation = variant.getAnnotation();
                    List<ConsequenceType> cts = new ArrayList<>(annotation.getConsequenceTypes().size());
                    for (ConsequenceType ct : annotation.getConsequenceTypes()) {
                        if (validCt(ct, ctFilter, biotypeFilter, gene::equals)) {
                            cts.add(ct);
                        }
                    }
                    if (cts.isEmpty()) {
                        logger.info("[{}] - Skip variant {}. CT filter not match", gene, variant.toString());
                        return;
                    }
                    knockout.setId(cts.get(0).getEnsemblGeneId());
                    StudyEntry studyEntry = variant.getStudies().get(0);
                    Integer sampleIdIdx = studyEntry.getFormatPositions().get(VariantQueryParser.SAMPLE_ID);
                    Integer fileIdxIdx = studyEntry.getFormatPositions().get(VariantQueryParser.FILE_IDX);
                    for (List<String> samplesDatum : studyEntry.getSamplesData()) {
                        String genotype = samplesDatum.get(0);
                        String sample = samplesDatum.get(sampleIdIdx);
                        int fileIdx = Integer.parseInt(samplesDatum.get(fileIdxIdx));

                        if (GenotypeClass.MAIN_ALT.test(genotype)) {
                            if (GenotypeClass.HOM_ALT.test(genotype)) {
                                FileEntry fileEntry = studyEntry.getFiles().get(fileIdx);
                                KnockoutByGene.KnockoutSample knockoutSample = knockout.getSample(sample);
                                knockoutSample.setId(sample);
                                for (ConsequenceType ct : cts) {
                                    KnockoutTranscript knockoutTranscript = knockoutSample.getTranscript(ct.getEnsemblTranscriptId());
                                    knockoutTranscript.setBiotype(ct.getBiotype());
                                    knockoutTranscript.addVariant(new KnockoutVariant(
                                            variant.toString(), genotype,
                                            fileEntry.getAttributes().get(StudyEntry.FILTER),
                                            fileEntry.getAttributes().get(StudyEntry.QUAL),
                                            KnockoutVariant.KnockoutType.HOM_ALT,
                                            ct.getSequenceOntologyTerms()));
                                }
                            } else if (GenotypeClass.HET_REF.test(genotype)) {
                                if (variant.getType().equals(VariantType.DELETION)) {
                                    deletionCandidateSamples.merge(sample, 1, Integer::sum);
                                }
                                compHetCandidateSamples.merge(sample, 1, Integer::sum);
                            } else if (GenotypeClass.HET_ALT.test(genotype)) {
                                multiAllelicCandidateSamples.merge(sample, 1, Integer::sum);
                            }
                        }
                    }
                } while (numSamples == limit);
            });

            logger.info("Found {} candidate variants for gene {}", numVariants, gene);

            for (Map.Entry<String, Integer> entry : compHetCandidateSamples.entrySet()) {
                if (entry.getValue() >= 2) {
                    // Check compound heterozygous for other samples
                    compHetKnockout(baseQuery, knockout, entry.getKey(), ctFilter, biotypeFilter);
                }
            }
            for (Map.Entry<String, Integer> entry : multiAllelicCandidateSamples.entrySet()) {
                if (entry.getValue() >= 2) {
                    // Check multiAllelic for other samples
                    multiAllelicKnockout(baseQuery, knockout, entry.getKey(), ctFilter, biotypeFilter);
                }
            }
            for (Map.Entry<String, Integer> entry : deletionCandidateSamples.entrySet()) {
                // Check overlapping deletions for other samples if there is any large deletion
                structuralKnockouts(baseQuery, knockout, entry.getKey(), ctFilter, biotypeFilter);
            }
            logger.info("Gene {} processed in {}", gene, TimeUtils.durationToString(stopWatch));
            logger.info("-----------------------------------------------------------");
            writeGeneFile(knockout);
        }

        private void compHetKnockout(Query baseQuery, KnockoutByGene knockoutByGene, String sampleId,
                                     Predicate<String> ctFilter, Predicate<String> biotypeFilter) throws Exception {
            Trio trio = getTrios().get(sampleId);
            if (trio == null) {
                return;
            }
            Query query = new Query(baseQuery)
                    .append(VariantQueryParam.GENE.key(), knockoutByGene.getName())
                    .append(VariantQueryParam.INCLUDE_SAMPLE.key(), trio.toList())
                    .append(VariantCatalogQueryUtils.FAMILY.key(), trio.getId())
                    .append(VariantQueryParam.INCLUDE_FORMAT.key(), "GT," + VariantQueryParser.FILE_IDX)
                    .append(VariantQueryParam.INCLUDE_FILE.key(), null)
//                            .append(VariantCatalogQueryUtils.FAMILY_DISORDER.key(), getDisorder())
                    .append(VariantCatalogQueryUtils.FAMILY_PROBAND.key(), sampleId)
                    .append(VariantCatalogQueryUtils.FAMILY_SEGREGATION.key(), COMPOUND_HETEROZYGOUS);
            try (VariantDBIterator iterator = variantStorageManager.iterator(query, new QueryOptions(), getToken())) {
                while (iterator.hasNext()) {
                    Variant variant = iterator.next();
                    StudyEntry studyEntry = variant.getStudies().get(0);
                    List<String> sampleData = studyEntry.getSamplesData().get(0);
                    FileEntry fileEntry = studyEntry.getFiles().get(Integer.parseInt(sampleData.get(1)));

                    KnockoutByGene.KnockoutSample sample = knockoutByGene.getSample(sampleId);
                    for (ConsequenceType ct : variant.getAnnotation().getConsequenceTypes()) {
                        if (validCt(ct, ctFilter, biotypeFilter, knockoutByGene.getName()::equals)) {
                            KnockoutTranscript knockoutTranscript = sample.getTranscript(ct.getEnsemblTranscriptId());
                            knockoutTranscript.setBiotype(ct.getBiotype());
                            knockoutTranscript.addVariant(new KnockoutVariant(
                                    variant.toString(), sampleData.get(0),
                                    fileEntry.getAttributes().get(StudyEntry.FILTER),
                                    fileEntry.getAttributes().get(StudyEntry.QUAL),
                                    KnockoutVariant.KnockoutType.COMP_HET,
                                    ct.getSequenceOntologyTerms()));
                        }
                    }
                }
            }
        }

        private void multiAllelicKnockout(Query baseQuery, KnockoutByGene knockout, String sampleId,
                                          Predicate<String> ctFilter, Predicate<String> biotypeFilter) throws Exception {
            Query query = new Query(baseQuery)
                    .append(VariantQueryParam.INCLUDE_GENOTYPE.key(), true)
                    .append(VariantQueryParam.INCLUDE_SAMPLE.key(), sampleId)
                    .append(VariantQueryParam.INCLUDE_FORMAT.key(), "GT," + VariantQueryParser.FILE_IDX)
                    .append(VariantQueryParam.INCLUDE_FILE.key(), null)
                    .append(VariantQueryParam.GENOTYPE.key(), sampleId + IS + "1/2");

            Map<String, KnockoutVariant> variants = new HashMap<>();
            iterate(query, variant -> {
                StudyEntry studyEntry = variant.getStudies().get(0);
                List<String> sampleData = studyEntry.getSamplesData().get(0);
                FileEntry fileEntry = studyEntry.getFiles().get(Integer.parseInt(sampleData.get(1)));

                Variant secVar = getSecondaryVariant(variant);
                KnockoutVariant knockoutVariant = new KnockoutVariant(
                        variant.toString(),
                        variant.getStudies().get(0).getSamplesData().get(0).get(0),
                        fileEntry.getAttributes().get(StudyEntry.FILTER),
                        fileEntry.getAttributes().get(StudyEntry.QUAL),
                        KnockoutVariant.KnockoutType.MULTI_ALLELIC, null
                );
                if (variants.put(variant.toString(), knockoutVariant) == null) {
                    // Variant not seen
                    if (variant.overlapWith(secVar, true)) {
                        // Add overlapping variant.
                        // If the overlapping variant is ever seen (so it also matches the filter criteria),
                        // the gene will be selected as knockout.
                        variants.put(secVar.toString(), new KnockoutVariant());
                    }
                } else {
                    KnockoutByGene.KnockoutSample sample = knockout.getSample(sampleId);
                    // The variant was already seen. i.e. there was a variant with this variant as secondary alternate
                    for (ConsequenceType ct : variant.getAnnotation().getConsequenceTypes()) {
                        if (validCt(ct, ctFilter, biotypeFilter, knockout.getName()::equals)) {
                            KnockoutTranscript knockoutTranscript = sample.getTranscript(ct.getEnsemblTranscriptId());
                            knockoutTranscript.setBiotype(ct.getBiotype());

                            String gt = sampleData.get(0);
                            knockoutTranscript.addVariant(new KnockoutVariant(
                                    variant.toString(), gt, null, null,
                                    KnockoutVariant.KnockoutType.MULTI_ALLELIC,
                                    ct.getSequenceOntologyTerms()));

                            KnockoutVariant secKnockoutVar = variants.get(secVar.toString());
                            knockoutTranscript.addVariant(new KnockoutVariant(
                                    secKnockoutVar.getVariant(),
                                    secKnockoutVar.getGenotype(),
                                    secKnockoutVar.getFilter(),
                                    secKnockoutVar.getQual(),
                                    KnockoutVariant.KnockoutType.MULTI_ALLELIC,
                                    ct.getSequenceOntologyTerms()));
                        }
                    }
                }
            });
        }

        protected void structuralKnockouts(Query baseQuery, KnockoutByGene knockout, String sampleId,
                                           Predicate<String> ctFilter, Predicate<String> biotypeFilter) throws Exception {
            Query query = new Query(baseQuery)
                    .append(VariantQueryParam.SAMPLE.key(), sampleId)
                    .append(VariantQueryParam.INCLUDE_SAMPLE.key(), sampleId)
                    .append(VariantQueryParam.INCLUDE_FORMAT.key(), "GT," + VariantQueryParser.FILE_IDX)
                    .append(VariantQueryParam.TYPE.key(), VariantType.DELETION);

            KnockoutByGene.KnockoutSample sample = knockout.getSample(sampleId);

            iterate(query, svVariant -> {
                Set<String> transcripts = new HashSet<>(svVariant.getAnnotation().getConsequenceTypes().size());
                for (ConsequenceType consequenceType : svVariant.getAnnotation().getConsequenceTypes()) {
                    if (validCt(consequenceType, ctFilter, biotypeFilter, knockout.getName()::equals)) {
                        transcripts.add(consequenceType.getEnsemblTranscriptId());
                    }
                }

                List<String> svSampleData = svVariant.getStudies().get(0).getSamplesData().get(0);
                FileEntry svFileEntry = svVariant.getStudies().get(0).getFiles().get(Integer.parseInt(svSampleData.get(1)));

                Query thisSvQuery = new Query(baseQuery)
                        .append(VariantQueryParam.SAMPLE.key(), sampleId)
                        .append(VariantQueryParam.INCLUDE_SAMPLE.key(), sampleId)
                        .append(VariantQueryParam.INCLUDE_FORMAT.key(), "GT," + VariantQueryParser.FILE_IDX)
                        .append(VariantQueryParam.REGION.key(), new Region(svVariant.getChromosome(), svVariant.getStart(), svVariant.getEnd()));

                iterate(thisSvQuery, variant -> {
                    if (variant.sameGenomicVariant(svVariant)) {
                        return;
                    }
                    for (ConsequenceType ct : variant.getAnnotation().getConsequenceTypes()) {
                        if (validCt(ct, ctFilter, biotypeFilter, knockout.getName()::equals)) {
                            if (transcripts.contains(ct.getEnsemblTranscriptId())) {
                                KnockoutTranscript knockoutTranscript = sample.getTranscript(ct.getEnsemblTranscriptId());
                                knockoutTranscript.setBiotype(ct.getBiotype());

                                StudyEntry studyEntry = variant.getStudies().get(0);
                                List<String> sampleData = studyEntry.getSamplesData().get(0);
                                FileEntry fileEntry = studyEntry.getFiles().get(Integer.parseInt(sampleData.get(1)));

                                knockoutTranscript.addVariant(new KnockoutVariant(
                                        variant.toString(), sampleData.get(0),
                                        fileEntry.getAttributes().get(StudyEntry.FILTER),
                                        fileEntry.getAttributes().get(StudyEntry.QUAL),
                                        KnockoutVariant.KnockoutType.DELETION_OVERLAP,
                                        ct.getSequenceOntologyTerms()));

                                knockoutTranscript.addVariant(new KnockoutVariant(
                                        svVariant.toString(), svSampleData.get(0),
                                        svFileEntry.getAttributes().get(StudyEntry.FILTER),
                                        svFileEntry.getAttributes().get(StudyEntry.QUAL),
                                        KnockoutVariant.KnockoutType.DELETION_OVERLAP,
                                        ct.getSequenceOntologyTerms()));
                            }
                        }
                    }
                });
            });
        }

        private void transposeGeneToSampleOutputFiles() throws IOException {
            Map<String, KnockoutBySample> bySampleMap = new HashMap<>();
            for (String gene : Iterables.concat(getOtherGenes(), getProteinCodingGenes())) {
                Path fileName = getGeneFileName(gene);
                if (Files.exists(fileName)) {
                    KnockoutByGene byGene = readGeneFile(gene);
                    for (KnockoutByGene.KnockoutSample sample : byGene.getSamples()) {
                        KnockoutBySample bySample = bySampleMap
                                .computeIfAbsent(sample.getId(), s -> new KnockoutBySample().setSample(new Sample().setId(s)));

                        bySample.getGene(gene).addTranscripts(sample.getTranscripts());

                    }
                }
            }

            for (Map.Entry<String, KnockoutBySample> entry : bySampleMap.entrySet()) {
                writeSampleFile(entry.getValue());
            }
        }
    }

    public interface VariantConsumer {
        void accept(Variant v) throws Exception;
    }

    private int iterate(Query query, VariantConsumer c)
            throws Exception {
        return iterate(query, new QueryOptions(), c);
    }

    private int iterate(Query query, QueryOptions queryOptions, VariantConsumer c)
            throws Exception {
        int numVariants;
        try (VariantDBIterator iterator = variantStorageManager.iterator(query, queryOptions, getToken())) {
            while (iterator.hasNext()) {
                c.accept(iterator.next());
            }
            numVariants = iterator.getCount();
        }
        return numVariants;
    }

    private void addGene(String variant, String gt, FileEntry fileEntry, ConsequenceType consequenceType,
                         Map<String, KnockoutGene> knockoutGenes,
                         KnockoutVariant.KnockoutType knockoutType) {
        addGene(variant, gt, fileEntry.getAttributes().get(StudyEntry.FILTER), fileEntry.getAttributes().get(StudyEntry.QUAL),
                consequenceType, knockoutGenes, knockoutType);
    }

    private void addGene(String variant, String gt, String filter, String qual, ConsequenceType consequenceType,
                         Map<String, KnockoutGene> knockoutGenes,
                         KnockoutVariant.KnockoutType knockoutType) {
        KnockoutGene gene = knockoutGenes.computeIfAbsent(consequenceType.getGeneName(), KnockoutGene::new);
        gene.setId(consequenceType.getEnsemblGeneId());
//        gene.setBiotype(consequenceType.getBiotype());
        if (StringUtils.isNotEmpty(consequenceType.getEnsemblTranscriptId())) {
            KnockoutTranscript t = gene.getTranscript(consequenceType.getEnsemblTranscriptId());
            t.setBiotype(consequenceType.getBiotype());
            t.addVariant(new KnockoutVariant(variant, gt, filter, qual, knockoutType, consequenceType.getSequenceOntologyTerms()));
        }
    }

    private boolean validCt(ConsequenceType consequenceType,
                            Predicate<String> ctFilter,
                            Predicate<String> biotypeFilter,
                            Predicate<String> geneFilter) {
        if (StringUtils.isEmpty(consequenceType.getGeneName())) {
            return false;
        }
        if (StringUtils.isEmpty(consequenceType.getEnsemblTranscriptId())) {
            return false;
        }
        if (!geneFilter.test(consequenceType.getGeneName())) {
            return false;
        }
        if (!biotypeFilter.test(consequenceType.getBiotype())) {
            return false;
        }
        if (consequenceType.getSequenceOntologyTerms().stream().noneMatch(so -> ctFilter.test(so.getName()))) {
            return false;
        }
        return true;
    }

    private Variant getSecondaryVariant(Variant variant) {
        Genotype gt = new Genotype(variant.getStudies().get(0).getSamplesData().get(0).get(0));
        Variant secVar = null;
        for (int allelesIdx : gt.getAllelesIdx()) {
            if (allelesIdx > 1) {
                AlternateCoordinate alt = variant.getStudies().get(0).getSecondaryAlternates().get(allelesIdx - 2);
                secVar = new Variant(
                        alt.getChromosome() == null ? variant.getChromosome() : alt.getChromosome(),
                        alt.getStart() == null ? variant.getStart() : alt.getStart(),
                        alt.getEnd() == null ? variant.getEnd() : alt.getEnd(),
                        alt.getReference() == null ? variant.getReference() : alt.getReference(),
                        alt.getAlternate() == null ? variant.getAlternate() : alt.getAlternate());
            }
        }
        return secVar;
    }

}
